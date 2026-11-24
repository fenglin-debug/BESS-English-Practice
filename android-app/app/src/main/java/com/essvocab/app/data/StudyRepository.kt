package com.essvocab.app.data

import com.essvocab.app.data.local.PackSummaryRow
import com.essvocab.app.data.local.ReviewDao
import com.essvocab.app.data.local.VocabDao
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.domain.fsrs.FsrsRating
import com.essvocab.app.domain.fsrs.FsrsScheduleOutcome
import com.essvocab.app.domain.fsrs.FsrsScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

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

    fun observeDueCount(nowMillis: Long = System.currentTimeMillis()): Flow<Int> =
        reviewDao.observeDueCount(nowMillis)

    fun observeUnseenCount(): Flow<Int> = reviewDao.observeUnseenCount()

    fun observeTodayReviewedCount(): Flow<Int> =
        reviewDao.observeTodayReviewedCount(startOfTodayMillis())

    fun observeMasteredCount(): Flow<Int> = reviewDao.observeMasteredCount()

    fun observeWords(packId: String?, query: String): Flow<List<WordWithReview>> =
        vocabDao.observeWords(packId, query.trim())

    /**
     * Builds a study queue prioritizing due review cards first, then up to remaining daily new cards.
     */
    suspend fun buildStudyQueue(
        packId: String?,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<WordWithReview> {
        val settings = preferencesRepository.getCurrentSettings()
        val dueWords = reviewDao.getDueWords(packId = packId, nowMillis = nowMillis, limit = 100)
        val learnedNewToday = reviewDao.countTodayNewLearned(startOfTodayMillis())
        val remainingNewQuota = if (packId != null) {
            // When user explicitly picks a specific pack, always allow at least 10 new words
            max(10, settings.dailyNewLimit - learnedNewToday)
        } else {
            max(0, settings.dailyNewLimit - learnedNewToday)
        }
        val newWords = if (remainingNewQuota > 0) {
            reviewDao.getNewWords(packId = packId, limit = remainingNewQuota)
        } else {
            emptyList()
        }
        return dueWords + newWords
    }

    suspend fun previewRatings(
        item: WordWithReview,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<FsrsRating, FsrsScheduleOutcome> {
        val retention = preferencesRepository.getCurrentSettings().desiredRetention
        return fsrsScheduler.previewAll(
            wordId = item.word.id,
            currentCard = item.card,
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
        val outcome = fsrsScheduler.review(
            wordId = item.word.id,
            currentCard = item.card,
            rating = rating,
            nowMillis = nowMillis,
            desiredRetention = retention,
        )
        reviewDao.recordReview(outcome.updatedCard, outcome.reviewLog)
        return outcome
    }

    private fun startOfTodayMillis(): Long {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
