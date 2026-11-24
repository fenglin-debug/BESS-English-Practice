package com.essvocab.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "vocab_packs")
data class VocabPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val orderIndex: Int,
    val wordCount: Int,
    val version: Int,
    val sha256: String,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "vocab_words",
    indices = [
        Index(value = ["packId", "isActive"]),
        Index(value = ["en"]),
        Index(value = ["zh"]),
    ],
)
data class VocabWordEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val seq: Int,
    val zh: String,
    val en: String,
    val ttsText: String,
    val abbreviationsCsv: String,
    val ipa: String,
    val exampleEn: String?,
    val exampleZh: String?,
    val note: String?,
    val audioUrl: String?,
    val isActive: Boolean = true,
) {
    fun abbreviationsList(): List<String> =
        abbreviationsCsv.split("|").map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Decoupled FSRS + Shanbay memory state keyed by wordId.
 * Never overwritten or deleted during remote vocabulary pack updates.
 */
@Entity(
    tableName = "review_cards",
    indices = [
        Index(value = ["dueAtMillis"]),
        Index(value = ["state"]),
    ],
)
data class ReviewCardEntity(
    @PrimaryKey val wordId: String,
    val state: Int = CARD_STATE_NEW,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val dueAtMillis: Long = 0L,
    val lastReviewAtMillis: Long? = null,
    val isFavorite: Boolean = false,
    val isMasteredBypass: Boolean = false,
) {
    companion object {
        const val CARD_STATE_NEW = 0
        const val CARD_STATE_LEARNING = 1
        const val CARD_STATE_REVIEW = 2
        const val CARD_STATE_RELEARNING = 3
    }
}

@Entity(
    tableName = "review_logs",
    indices = [Index(value = ["reviewedAtMillis"]), Index(value = ["wordId"])],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: String,
    val rating: Int,
    val stateBefore: Int,
    val stabilityAfter: Double,
    val difficultyAfter: Double,
    val scheduledDays: Int,
    val reviewedAtMillis: Long,
)

@Entity(tableName = "daily_checkins")
data class DailyCheckInEntity(
    @PrimaryKey val dateKey: String,
    val reviewedCount: Int,
    val newLearnedCount: Int,
    val checkedInAtMillis: Long,
)

data class WordWithReview(
    @Embedded val word: VocabWordEntity,
    @Embedded(prefix = "card_") val card: ReviewCardEntity?,
    @ColumnInfo(name = "packName") val packName: String,
)

data class PackSummaryRow(
    val id: String,
    val name: String,
    val description: String,
    val orderIndex: Int,
    val version: Int,
    val totalWords: Int,
    val learnedWords: Int,
    val dueWords: Int,
)
