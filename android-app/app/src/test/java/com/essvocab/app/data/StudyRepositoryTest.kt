package com.essvocab.app.data

import com.essvocab.app.data.local.DailyCheckInEntity
import com.essvocab.app.data.local.PackSummaryRow
import com.essvocab.app.data.local.ReviewCardEntity
import com.essvocab.app.data.local.ReviewDao
import com.essvocab.app.data.local.ReviewLogEntity
import com.essvocab.app.data.local.VocabDao
import com.essvocab.app.data.local.VocabPackEntity
import com.essvocab.app.data.local.VocabWordEntity
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.data.preferences.AppSettings
import com.essvocab.app.data.preferences.StudySettingsSource
import com.essvocab.app.domain.fsrs.FsrsRating
import com.essvocab.app.domain.fsrs.FsrsScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StudyRepositoryTest {

    private lateinit var vocabDao: FakeVocabDao
    private lateinit var reviewDao: FakeReviewDao
    private lateinit var settingsSource: FakeStudySettingsSource
    private lateinit var repository: StudyRepository

    @Before
    fun setUp() {
        vocabDao = FakeVocabDao()
        reviewDao = FakeReviewDao(vocabDao)
        settingsSource = FakeStudySettingsSource()
        repository = StudyRepository(
            vocabDao = vocabDao,
            reviewDao = reviewDao,
            preferencesRepository = settingsSource,
            fsrsScheduler = FsrsScheduler(),
        )
    }

    @Test
    fun dailyBookQueueRespectsDailyReviewLimitAndPreservesNewWordQuota() = runTest {
        val now = System.currentTimeMillis()
        vocabDao.packs["basic_system"] = VocabPackEntity(
            id = "basic_system",
            name = "基础术语与系统",
            description = "",
            orderIndex = 1,
            wordCount = 15,
            version = 1,
            sha256 = "abc",
            updatedAtMillis = now,
        )

        // 10 overdue review words
        for (i in 1..10) {
            val id = "due_$i"
            vocabDao.words[id] = makeWord(id, seq = i)
            reviewDao.cards[id] = ReviewCardEntity(
                wordId = id,
                state = ReviewCardEntity.CARD_STATE_REVIEW,
                stability = 5.0,
                difficulty = 5.0,
                reps = 2,
                dueAtMillis = now - 60_000L * (11 - i),
                lastReviewAtMillis = now - 86_400_000L,
            )
        }
        // 5 unseen new words
        for (i in 11..15) {
            val id = "new_$i"
            vocabDao.words[id] = makeWord(id, seq = i)
        }

        // Simulate 1 distinct old word already reviewed earlier today
        reviewDao.logs.add(
            ReviewLogEntity(
                id = 1L,
                wordId = "already_reviewed_today",
                rating = FsrsRating.GOOD.value,
                stateBefore = ReviewCardEntity.CARD_STATE_REVIEW,
                stabilityAfter = 10.0,
                difficultyAfter = 4.5,
                scheduledDays = 10,
                reviewedAtMillis = now,
            ),
        )

        settingsSource.settings = AppSettings(
            dailyNewLimit = 3,
            dailyReviewLimit = 4,
        )

        val queue = repository.buildStudyQueue(
            mode = StudyMode.DAILY_BOOK,
            packId = null,
            nowMillis = now,
        )

        val dueInQueue = queue.filter { it.word.id.startsWith("due_") }
        val newInQueue = queue.filter { it.word.id.startsWith("new_") }

        assertEquals("Due quota should be dailyReviewLimit(4) - alreadyReviewedToday(1) = 3", 3, dueInQueue.size)
        assertEquals("New word quota must not be blocked by overdue reviews", 3, newInQueue.size)
        assertEquals(6, queue.size)
    }

    @Test
    fun submitReviewAndUndoRestoreNewWordCompletely() = runTest {
        val word = makeWord("ess_bess", seq = 1)
        val item = WordWithReview(word = word, card = null, packName = "基础术语与系统")
        val now = 1_750_000_000_000L

        val recorded = repository.submitReview(item, FsrsRating.GOOD, nowMillis = now)
        assertNull(recorded.previousCard)
        assertNotNull(reviewDao.getCard("ess_bess"))
        assertEquals(1, reviewDao.logs.size)

        repository.undoReview("ess_bess", recorded.previousCard, recorded.logId)
        assertNull("Undoing a brand-new card must remove it from review_cards", reviewDao.getCard("ess_bess"))
        assertTrue("Undoing must delete the recorded review log", reviewDao.logs.isEmpty())
    }

    @Test
    fun submitReviewAndUndoRestoreExistingCardExactly() = runTest {
        val word = makeWord("ess_rte", seq = 2)
        val originalCard = ReviewCardEntity(
            wordId = "ess_rte",
            state = ReviewCardEntity.CARD_STATE_REVIEW,
            stability = 8.5,
            difficulty = 4.2,
            elapsedDays = 7,
            scheduledDays = 9,
            reps = 3,
            lapses = 1,
            dueAtMillis = 1_750_000_000_000L,
            lastReviewAtMillis = 1_749_200_000_000L,
            isFavorite = true,
            isMasteredBypass = false,
        )
        reviewDao.cards["ess_rte"] = originalCard
        val item = WordWithReview(word = word, card = originalCard, packName = "性能参数")

        val recorded = repository.submitReview(item, FsrsRating.AGAIN, nowMillis = 1_750_100_000_000L)
        val afterReview = reviewDao.getCard("ess_rte")!!
        assertEquals(ReviewCardEntity.CARD_STATE_RELEARNING, afterReview.state)
        assertEquals(2, afterReview.lapses)
        assertEquals(1, reviewDao.logs.size)

        repository.undoReview("ess_rte", recorded.previousCard, recorded.logId)
        assertEquals(originalCard, reviewDao.getCard("ess_rte"))
        assertTrue(reviewDao.logs.isEmpty())
    }

    @Test
    fun markTooEasyAndUndoRestoreUnbypassedCard() = runTest {
        val word = makeWord("ess_pcs", seq = 3)
        val originalCard = ReviewCardEntity(
            wordId = "ess_pcs",
            state = ReviewCardEntity.CARD_STATE_LEARNING,
            stability = 1.2,
            difficulty = 5.0,
            reps = 1,
            dueAtMillis = 1_750_000_000_000L,
            lastReviewAtMillis = 1_749_900_000_000L,
            isMasteredBypass = false,
        )
        reviewDao.cards["ess_pcs"] = originalCard
        val item = WordWithReview(word = word, card = originalCard, packName = "储能变流器与电气")

        val recorded = repository.markTooEasy(item, nowMillis = 1_750_100_000_000L)
        assertTrue(reviewDao.getCard("ess_pcs")!!.isMasteredBypass)
        assertEquals(1, reviewDao.logs.size)

        repository.undoReview("ess_pcs", recorded.previousCard, recorded.logId)
        val restored = reviewDao.getCard("ess_pcs")!!
        assertFalse(restored.isMasteredBypass)
        assertEquals(originalCard, restored)
        assertTrue(reviewDao.logs.isEmpty())
    }

    private fun makeWord(id: String, seq: Int) = VocabWordEntity(
        id = id,
        packId = "basic_system",
        seq = seq,
        zh = "测试术语 $seq",
        en = "Test term $seq",
        ttsText = "Test term $seq",
        abbreviationsCsv = "",
        ipa = "",
        exampleEn = "Example sentence for $id.",
        exampleZh = "示例中文 $seq。",
        note = "工程师注解 $seq。",
        audioUrl = "audio/$id.mp3",
        isActive = true,
    )

    private class FakeStudySettingsSource(
        var settings: AppSettings = AppSettings(),
    ) : StudySettingsSource {
        override suspend fun getCurrentSettings(): AppSettings = settings
    }

    private class FakeVocabDao : VocabDao {
        val packs = linkedMapOf<String, VocabPackEntity>()
        val words = linkedMapOf<String, VocabWordEntity>()
        private val wordsFlow = MutableStateFlow<List<VocabWordEntity>>(emptyList())

        override fun observePackSummaries(nowMillis: Long): Flow<List<PackSummaryRow>> =
            wordsFlow.map { emptyList() }

        override suspend fun getAllPacks(): List<VocabPackEntity> = packs.values.toList()
        override suspend fun upsertPack(pack: VocabPackEntity) { packs[pack.id] = pack }
        override suspend fun upsertWords(words: List<VocabWordEntity>) { words.forEach { this.words[it.id] = it } }
        override suspend fun deactivateWordsNotIn(packId: String, activeWordIds: List<String>) = Unit
        override suspend fun deactivateAllWordsInPack(packId: String) = Unit
        override suspend fun deletePacksNotIn(activePackIds: List<String>) = Unit
        override suspend fun deactivateWordsOutsidePacks(activePackIds: List<String>) = Unit
        override fun observeTotalActiveWords(): Flow<Int> = wordsFlow.map { words.values.count { w -> w.isActive } }
        override suspend fun countActiveWords(): Int = words.values.count { it.isActive }
        override fun observeWords(packId: String?, query: String): Flow<List<WordWithReview>> = wordsFlow.map { emptyList() }
    }

    private class FakeReviewDao(
        private val vocabDao: FakeVocabDao,
    ) : ReviewDao {
        val cards = linkedMapOf<String, ReviewCardEntity>()
        val logs = mutableListOf<ReviewLogEntity>()
        val checkIns = linkedMapOf<String, DailyCheckInEntity>()
        private var nextLogId = 100L

        override suspend fun getDueWords(packId: String?, nowMillis: Long, limit: Int): List<WordWithReview> {
            return vocabDao.words.values
                .filter { it.isActive && (packId == null || it.packId == packId) }
                .mapNotNull { w ->
                    val card = cards[w.id] ?: return@mapNotNull null
                    if (!card.isMasteredBypass && card.dueAtMillis <= nowMillis) {
                        WordWithReview(w, card, vocabDao.packs[w.packId]?.name ?: "")
                    } else {
                        null
                    }
                }
                .sortedBy { it.card!!.dueAtMillis }
                .take(limit)
        }

        override suspend fun getNewWords(packId: String?, limit: Int): List<WordWithReview> {
            return vocabDao.words.values
                .filter { it.isActive && (packId == null || it.packId == packId) && cards[it.id] == null }
                .sortedBy { it.seq }
                .take(limit)
                .map { WordWithReview(it, null, vocabDao.packs[it.packId]?.name ?: "") }
        }

        override suspend fun getReinforcementWords(packId: String?, limit: Int): List<WordWithReview> = emptyList()

        override suspend fun getCard(wordId: String): ReviewCardEntity? = cards[wordId]

        override suspend fun upsertCard(card: ReviewCardEntity) {
            cards[card.wordId] = card
        }

        override suspend fun insertLog(log: ReviewLogEntity): Long {
            val assignedId = if (log.id == 0L) nextLogId++ else log.id
            logs.add(log.copy(id = assignedId))
            return assignedId
        }

        override suspend fun deleteCard(wordId: String) {
            cards.remove(wordId)
        }

        override suspend fun deleteLogById(logId: Long) {
            logs.removeAll { it.id == logId }
        }

        override fun observeTotalActiveWordsForPack(packId: String?): Flow<Int> = MutableStateFlow(0)
        override fun observeDueCountForPack(packId: String?, nowMillis: Long): Flow<Int> = MutableStateFlow(0)
        override fun observeUnseenCountForPack(packId: String?): Flow<Int> = MutableStateFlow(0)
        override fun observeMasteredCountForPack(packId: String?): Flow<Int> = MutableStateFlow(0)
        override fun observeTodayReviewedCount(startOfDayMillis: Long): Flow<Int> = MutableStateFlow(0)
        override fun observeTodayNewLearnedCount(startOfDayMillis: Long): Flow<Int> = MutableStateFlow(0)

        override suspend fun countTodayNewLearned(startOfDayMillis: Long): Int =
            logs.filter { it.reviewedAtMillis >= startOfDayMillis && it.stateBefore == 0 }
                .map { it.wordId }.distinct().size

        override suspend fun countTodayReviewed(startOfDayMillis: Long): Int =
            logs.count { it.reviewedAtMillis >= startOfDayMillis }

        override fun observeTodayOldReviewedWordCount(startOfDayMillis: Long): Flow<Int> = MutableStateFlow(0)

        override suspend fun countTodayOldReviewedWords(startOfDayMillis: Long): Int =
            logs.filter { it.reviewedAtMillis >= startOfDayMillis && it.stateBefore != 0 }
                .map { it.wordId }.distinct().size

        override fun observeTotalReviewsAllTime(): Flow<Int> = MutableStateFlow(logs.size)
        override fun observeNotebookWords(): Flow<List<WordWithReview>> = MutableStateFlow(emptyList())
        override suspend fun getNotebookStudyQueue(limit: Int): List<WordWithReview> = emptyList()
        override fun observeNotebookCount(): Flow<Int> = MutableStateFlow(0)
        override fun observeAllCheckIns(): Flow<List<DailyCheckInEntity>> = MutableStateFlow(checkIns.values.toList())
        override suspend fun upsertCheckIn(checkIn: DailyCheckInEntity) { checkIns[checkIn.dateKey] = checkIn }
    }
}
