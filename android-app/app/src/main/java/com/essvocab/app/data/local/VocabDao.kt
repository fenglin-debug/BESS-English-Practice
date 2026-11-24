package com.essvocab.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabDao {

    @Query(
        """
        SELECT
            p.id,
            p.name,
            p.description,
            p.orderIndex,
            p.version,
            COUNT(w.id) AS totalWords,
            SUM(CASE WHEN c.wordId IS NOT NULL AND (c.reps > 0 OR c.isMasteredBypass = 1) THEN 1 ELSE 0 END) AS learnedWords,
            SUM(CASE WHEN c.wordId IS NOT NULL AND c.isMasteredBypass = 0 AND c.dueAtMillis <= :nowMillis THEN 1 ELSE 0 END) AS dueWords
        FROM vocab_packs p
        LEFT JOIN vocab_words w ON w.packId = p.id AND w.isActive = 1
        LEFT JOIN review_cards c ON c.wordId = w.id
        GROUP BY p.id
        ORDER BY p.orderIndex ASC
        """,
    )
    fun observePackSummaries(nowMillis: Long): Flow<List<PackSummaryRow>>

    @Query("SELECT * FROM vocab_packs ORDER BY orderIndex ASC")
    suspend fun getAllPacks(): List<VocabPackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(pack: VocabPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWords(words: List<VocabWordEntity>)

    @Query("UPDATE vocab_words SET isActive = 0 WHERE packId = :packId AND id NOT IN (:activeWordIds)")
    suspend fun deactivateWordsNotIn(packId: String, activeWordIds: List<String>)

    @Query("UPDATE vocab_words SET isActive = 0 WHERE packId = :packId")
    suspend fun deactivateAllWordsInPack(packId: String)

    @Query("DELETE FROM vocab_packs WHERE id NOT IN (:activePackIds)")
    suspend fun deletePacksNotIn(activePackIds: List<String>)

    @Query("UPDATE vocab_words SET isActive = 0 WHERE packId NOT IN (:activePackIds)")
    suspend fun deactivateWordsOutsidePacks(activePackIds: List<String>)

    @Query("SELECT COUNT(*) FROM vocab_words WHERE isActive = 1")
    fun observeTotalActiveWords(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocab_words WHERE isActive = 1")
    suspend fun countActiveWords(): Int

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
          AND (:packId IS NULL OR w.packId = :packId)
          AND (
              :query = ''
              OR w.en LIKE '%' || :query || '%'
              OR w.zh LIKE '%' || :query || '%'
              OR w.abbreviationsCsv LIKE '%' || :query || '%'
          )
        ORDER BY p.orderIndex ASC, w.seq ASC
        """,
    )
    fun observeWords(packId: String?, query: String): Flow<List<WordWithReview>>

    @Transaction
    suspend fun replacePackWords(pack: VocabPackEntity, words: List<VocabWordEntity>) {
        upsertPack(pack)
        if (words.isEmpty()) {
            deactivateAllWordsInPack(pack.id)
        } else {
            upsertWords(words)
            deactivateWordsNotIn(pack.id, words.map { it.id })
        }
    }

    @Transaction
    suspend fun pruneRemovedPacks(activePackIds: List<String>) {
        if (activePackIds.isNotEmpty()) {
            deletePacksNotIn(activePackIds)
            deactivateWordsOutsidePacks(activePackIds)
        }
    }
}
