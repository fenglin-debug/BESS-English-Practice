package com.essvocab.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    @Query(
        """
        SELECT
            w.*,
            p.name AS packName,
            c.wordId AS card_wordId,
            c.state AS card_state,
            c.stability AS card_stability,
            c.difficulty AS card_difficulty,
            c.elapsedDays AS card_elapsedDays,
            c.scheduledDays AS card_scheduledDays,
            c.reps AS card_reps,
            c.lapses AS card_lapses,
            c.dueAtMillis AS card_dueAtMillis,
            c.lastReviewAtMillis AS card_lastReviewAtMillis,
            c.isFavorite AS card_isFavorite,
            c.isMasteredBypass AS card_isMasteredBypass
        FROM vocab_words w
        INNER JOIN vocab_packs p ON p.id = w.packId
        INNER JOIN review_cards c ON c.wordId = w.id
        WHERE w.isActive = 1
          AND c.isMasteredBypass = 0
          AND c.dueAtMillis <= :nowMillis
          AND (:packId IS NULL OR w.packId = :packId)
        ORDER BY c.dueAtMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun getDueWords(packId: String?, nowMillis: Long, limit: Int): List<WordWithReview>

    @Query(
        """
        SELECT
            w.*,
            p.name AS packName,
            c.wordId AS card_wordId,
            c.state AS card_state,
            c.stability AS card_stability,
            c.difficulty AS card_difficulty,
            c.elapsedDays AS card_elapsedDays,
            c.scheduledDays AS card_scheduledDays,
            c.reps AS card_reps,
            c.lapses AS card_lapses,
            c.dueAtMillis AS card_dueAtMillis,
            c.lastReviewAtMillis AS card_lastReviewAtMillis,
            c.isFavorite AS card_isFavorite,
            c.isMasteredBypass AS card_isMasteredBypass
        FROM vocab_words w
        INNER JOIN vocab_packs p ON p.id = w.packId
        LEFT JOIN review_cards c ON c.wordId = w.id
        WHERE w.isActive = 1
          AND c.wordId IS NULL
          AND (:packId IS NULL OR w.packId = :packId)
        ORDER BY p.orderIndex ASC, w.seq ASC
        LIMIT :limit
        """,
    )
    suspend fun getNewWords(packId: String?, limit: Int): List<WordWithReview>

    @Query(
        """
        SELECT
            w.*,
            p.name AS packName,
            c.wordId AS card_wordId,
            c.state AS card_state,
            c.stability AS card_stability,
            c.difficulty AS card_difficulty,
            c.elapsedDays AS card_elapsedDays,
            c.scheduledDays AS card_scheduledDays,
            c.reps AS card_reps,
            c.lapses AS card_lapses,
            c.dueAtMillis AS card_dueAtMillis,
            c.lastReviewAtMillis AS card_lastReviewAtMillis,
            c.isFavorite AS card_isFavorite,
            c.isMasteredBypass AS card_isMasteredBypass
        FROM vocab_words w
        INNER JOIN vocab_packs p ON p.id = w.packId
        INNER JOIN review_cards c ON c.wordId = w.id
        WHERE w.isActive = 1
          AND c.isMasteredBypass = 0
          AND (:packId IS NULL OR w.packId = :packId)
        ORDER BY c.stability ASC, c.dueAtMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun getReinforcementWords(packId: String?, limit: Int): List<WordWithReview>

    @Query("SELECT * FROM review_cards WHERE wordId = :wordId")
    suspend fun getCard(wordId: String): ReviewCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: ReviewCardEntity)

    @Insert
    suspend fun insertLog(log: ReviewLogEntity)

    @Transaction
    suspend fun recordReview(card: ReviewCardEntity, log: ReviewLogEntity) {
        upsertCard(card)
        insertLog(log)
    }

    @Query(
        """
        SELECT COUNT(*) FROM vocab_words w
        WHERE w.isActive = 1 AND (:packId IS NULL OR w.packId = :packId)
        """,
    )
    fun observeTotalActiveWordsForPack(packId: String?): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM review_cards c
        INNER JOIN vocab_words w ON w.id = c.wordId
        WHERE w.isActive = 1
          AND (:packId IS NULL OR w.packId = :packId)
          AND c.isMasteredBypass = 0
          AND c.dueAtMillis <= :nowMillis
        """,
    )
    fun observeDueCountForPack(packId: String?, nowMillis: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM vocab_words w
        LEFT JOIN review_cards c ON c.wordId = w.id
        WHERE w.isActive = 1
          AND (:packId IS NULL OR w.packId = :packId)
          AND c.wordId IS NULL
        """,
    )
    fun observeUnseenCountForPack(packId: String?): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM review_cards c
        INNER JOIN vocab_words w ON w.id = c.wordId
        WHERE w.isActive = 1
          AND (:packId IS NULL OR w.packId = :packId)
          AND (c.isMasteredBypass = 1 OR (c.state = 2 AND c.stability >= 7.0))
        """,
    )
    fun observeMasteredCountForPack(packId: String?): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAtMillis >= :startOfDayMillis")
    fun observeTodayReviewedCount(startOfDayMillis: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM review_logs
        WHERE reviewedAtMillis >= :startOfDayMillis AND stateBefore = 0
        """,
    )
    fun observeTodayNewLearnedCount(startOfDayMillis: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT wordId) FROM review_logs
        WHERE reviewedAtMillis >= :startOfDayMillis AND stateBefore = 0
        """,
    )
    suspend fun countTodayNewLearned(startOfDayMillis: Long): Int

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAtMillis >= :startOfDayMillis")
    suspend fun countTodayReviewed(startOfDayMillis: Long): Int

    @Query("SELECT COUNT(*) FROM review_logs")
    fun observeTotalReviewsAllTime(): Flow<Int>

    @Query(
        """
        SELECT
            w.*,
            p.name AS packName,
            c.wordId AS card_wordId,
            c.state AS card_state,
            c.stability AS card_stability,
            c.difficulty AS card_difficulty,
            c.elapsedDays AS card_elapsedDays,
            c.scheduledDays AS card_scheduledDays,
            c.reps AS card_reps,
            c.lapses AS card_lapses,
            c.dueAtMillis AS card_dueAtMillis,
            c.lastReviewAtMillis AS card_lastReviewAtMillis,
            c.isFavorite AS card_isFavorite,
            c.isMasteredBypass AS card_isMasteredBypass
        FROM vocab_words w
        INNER JOIN vocab_packs p ON p.id = w.packId
        INNER JOIN review_cards c ON c.wordId = w.id
        WHERE w.isActive = 1
          AND (c.isFavorite = 1 OR c.lapses >= 1)
        ORDER BY c.isFavorite DESC, c.lapses DESC, w.seq ASC
        """,
    )
    fun observeNotebookWords(): Flow<List<WordWithReview>>

    @Query(
        """
        SELECT
            w.*,
            p.name AS packName,
            c.wordId AS card_wordId,
            c.state AS card_state,
            c.stability AS card_stability,
            c.difficulty AS card_difficulty,
            c.elapsedDays AS card_elapsedDays,
            c.scheduledDays AS card_scheduledDays,
            c.reps AS card_reps,
            c.lapses AS card_lapses,
            c.dueAtMillis AS card_dueAtMillis,
            c.lastReviewAtMillis AS card_lastReviewAtMillis,
            c.isFavorite AS card_isFavorite,
            c.isMasteredBypass AS card_isMasteredBypass
        FROM vocab_words w
        INNER JOIN vocab_packs p ON p.id = w.packId
        INNER JOIN review_cards c ON c.wordId = w.id
        WHERE w.isActive = 1
          AND (c.isFavorite = 1 OR c.lapses >= 1)
        ORDER BY c.isFavorite DESC, c.lapses DESC, w.seq ASC
        LIMIT :limit
        """,
    )
    suspend fun getNotebookStudyQueue(limit: Int): List<WordWithReview>

    @Query(
        """
        SELECT COUNT(*) FROM review_cards c
        INNER JOIN vocab_words w ON w.id = c.wordId
        WHERE w.isActive = 1 AND (c.isFavorite = 1 OR c.lapses >= 1)
        """,
    )
    fun observeNotebookCount(): Flow<Int>

    @Query("SELECT * FROM daily_checkins ORDER BY dateKey DESC")
    fun observeAllCheckIns(): Flow<List<DailyCheckInEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckIn(checkIn: DailyCheckInEntity)
}
