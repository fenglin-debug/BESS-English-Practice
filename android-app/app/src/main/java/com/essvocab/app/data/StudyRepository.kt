package com.essvocab.app.data

import com.essvocab.app.data.local.DailyCheckInEntity
import com.essvocab.app.data.local.PackSummaryRow
import com.essvocab.app.data.local.ReviewCardEntity
import com.essvocab.app.data.local.ReviewDao
import com.essvocab.app.data.local.VocabDao
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.domain.fsrs.FsrsRating
import com.essvocab.app.domain.fsrs.FsrsScheduleOutcome
import com.essvocab.app.domain.fsrs.FsrsScheduler
import com.essvocab.app.domain.shanbay.ShanbayMechanics
import com.essvocab.app.domain.shanbay.StreakSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

enum class StudyMode {
    DAILY_BOOK,
    EXTRA_PRACTICE,
    NOTEBOOK,
}

data class BookStats(
    val totalWords: Int = 0,
    val masteredWords: Int = 0,
    val dueWords: Int = 0,
    val unseenWords: Int = 0,
    val todayNewLearned: Int = 0,
    val todayReviewed: Int = 0,
)

@Singleton
class StudyRepository @Inject constructor(
    private val vocabDao: VocabDao,
    private val reviewDao: ReviewDao,
    private val preferencesRepository: AppPreferencesRepository,
    private val fsrsScheduler: FsrsScheduler,
) {
    fun observePackSummaries(nowMillis: Long = System.currentTimeMillis()): Flow<List<PackSummaryRow>> =
        vocabDao.observePackSummaries(nowMillis)

    fun observeTotalActiveWords(): Flow<Int> = vocabDao.observeTotalActiveWords()

    fun observeBookStats(packId: String?): Flow<BookStats> {
        val scopedPackId = packId?.takeIf { it.isNotBlank() }
        val startOfDay = startOfTodayMillis()
        val nowMillis = System.currentTimeMillis()
        return combine(
            reviewDao.observeTotalActiveWordsForPack(scopedPackId),
            reviewDao.observeMasteredCountForPack(scopedPackId),
            reviewDao.observeDueCountForPack(scopedPackId, nowMillis),
            reviewDao.observeUnseenCountForPack(scopedPackId),
            combine(
                reviewDao.observeTodayNewLearnedCount(startOfDay),
                reviewDao.observeTodayReviewedCount(startOfDay),
            ) { newToday, revToday -> newToday to revToday },
        ) { total, mastered, due, unseen, (newToday, revToday) ->
            BookStats(
                totalWords = total,
                masteredWords = mastered,
                dueWords = due,
                unseenWords = unseen,
                todayNewLearned = newToday,
                todayReviewed = revToday,
            )
        }
    }

    fun observeTotalReviewsAllTime(): Flow<Int> = reviewDao.observeTotalReviewsAllTime()

    fun observeNotebookWords(): Flow<List<WordWithReview>> = reviewDao.observeNotebookWords()

    fun observeNotebookCount(): Flow<Int> = reviewDao.observeNotebookCount()

    fun observeCheckInStreak(): Flow<StreakSummary> =
        reviewDao.observeAllCheckIns().map { list ->
            ShanbayMechanics.calculateStreak(list.map { it.dateKey })
        }

    fun observeWords(packId: String?, query: String): Flow<List<WordWithReview>> =
        vocabDao.observeWords(packId, query.trim())

    /**
     * Builds a study queue according to Shanbay mode:
     * - DAILY_BOOK: due reviews first, then remaining daily new quota.
     * - EXTRA_PRACTICE: additional unseen words, falling back to lowest-stability words.
     * - NOTEBOOK: favorited & lapsed difficult words.
     */
    suspend fun buildStudyQueue(
        mode: StudyMode,
        packId: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WordWithReview> {
        val scopedPackId = packId?.takeIf { it.isNotBlank() }
        return when (mode) {
            StudyMode.DAILY_BOOK -> {
                val settings = preferencesRepository.getCurrentSettings()
                val dueWords = reviewDao.getDueWords(packId = scopedPackId, nowMillis = nowMillis, limit = 40)
                val learnedNewToday = reviewDao.countTodayNewLearned(startOfTodayMillis())
                val remainingNewQuota = max(0, settings.dailyNewLimit - learnedNewToday)
                val newWords = if (remainingNewQuota > 0) {
                    reviewDao.getNewWords(packId = scopedPackId, limit = remainingNewQuota)
                } else {
                    emptyList()
                }
                dueWords + newWords
            }

            StudyMode.EXTRA_PRACTICE -> {
                val extraNew = reviewDao.getNewWords(packId = scopedPackId, limit = 10)
                if (extraNew.size >= 10) {
                    extraNew
                } else {
                    val existingIds = extraNew.map { it.word.id }.toSet()
                    val reinforce = reviewDao.getReinforcementWords(packId = scopedPackId, limit = 15)
                        .filter { it.word.id !in existingIds }
                    (extraNew + reinforce).take(10)
                }
            }

            StudyMode.NOTEBOOK -> reviewDao.getNotebookStudyQueue(limit = 30)
        }
    }

    suspend fun previewRatings(
        item: WordWithReview,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<FsrsRating, FsrsScheduleOutcome> {
        val retention = preferencesRepository.getCurrentSettings().desiredRetention
        val latestCard = reviewDao.getCard(item.word.id) ?: item.card
        return fsrsScheduler.previewAll(
            wordId = item.word.id,
            currentCard = latestCard,
            nowMillis = nowMillis,
            desiredRetention = retention,
        )
    }

    suspend fun submitReview(
        item: WordWithReview,
        rating: FsrsRating,
        nowMillis: Long = System.currentTimeMillis(),
    ): FsrsScheduleOutcome {
        val retention = preferencesRepository.getCurrentSettings().desiredRetention
        val latestCard = reviewDao.getCard(item.word.id) ?: item.card
        val outcome = fsrsScheduler.review(
            wordId = item.word.id,
            currentCard = latestCard,
            rating = rating,
            nowMillis = nowMillis,
            desiredRetention = retention,
        )
        val mergedCard = outcome.updatedCard.copy(
            isFavorite = latestCard?.isFavorite ?: false,
            isMasteredBypass = false,
        )
        reviewDao.recordReview(mergedCard, outcome.reviewLog)
        return outcome.copy(updatedCard = mergedCard)
    }

    /**
     * Toggles whether a word is starred into the user's 生词本 (Notebook),
     * preserving all existing FSRS parameters.
     */
    suspend fun toggleFavorite(wordId: String): Boolean {
        val existing = reviewDao.getCard(wordId)
        val newFav = !(existing?.isFavorite ?: false)
        val updated = existing?.copy(isFavorite = newFav)
            ?: ReviewCardEntity(wordId = wordId, isFavorite = newFav)
        reviewDao.upsertCard(updated)
        return newFav
    }

    /**
     * Shanbay "斩" (Too easy / Master bypass): immediately marks the word mastered
     * and excludes it from future due queues while logging an EASY evaluation.
     */
    suspend fun markTooEasy(
        item: WordWithReview,
        nowMillis: Long = System.currentTimeMillis(),
    ): FsrsScheduleOutcome {
        val retention = preferencesRepository.getCurrentSettings().desiredRetention
        val latestCard = reviewDao.getCard(item.word.id) ?: item.card
        val outcome = fsrsScheduler.review(
            wordId = item.word.id,
            currentCard = latestCard,
            rating = FsrsRating.EASY,
            nowMillis = nowMillis,
            desiredRetention = retention,
        )
        val bypassed = outcome.updatedCard.copy(
            isFavorite = latestCard?.isFavorite ?: false,
            isMasteredBypass = true,
        )
        reviewDao.recordReview(bypassed, outcome.reviewLog)
        return outcome.copy(updatedCard = bypassed)
    }

    suspend fun restoreFromMastered(wordId: String, nowMillis: Long = System.currentTimeMillis()) {
        val existing = reviewDao.getCard(wordId) ?: return
        reviewDao.upsertCard(
            existing.copy(
                isMasteredBypass = false,
                dueAtMillis = nowMillis,
            ),
        )
    }

    suspend fun recordTodayCheckIn(nowMillis: Long = System.currentTimeMillis()) {
        val startOfDay = startOfTodayMillis()
        val reviewed = reviewDao.countTodayReviewed(startOfDay)
        val newLearned = reviewDao.countTodayNewLearned(startOfDay)
        val dateKey = LocalDate.now().toString()
        reviewDao.upsertCheckIn(
            DailyCheckInEntity(
                dateKey = dateKey,
                reviewedCount = reviewed,
                newLearnedCount = newLearned,
                checkedInAtMillis = nowMillis,
            ),
        )
    }

    private fun startOfTodayMillis(): Long {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
