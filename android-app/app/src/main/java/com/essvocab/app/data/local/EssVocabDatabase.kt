package com.essvocab.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VocabPackEntity::class,
        VocabWordEntity::class,
        ReviewCardEntity::class,
        ReviewLogEntity::class,
        DailyCheckInEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class EssVocabDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE review_cards ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE review_cards ADD COLUMN isMasteredBypass INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_checkins` (
                        `dateKey` TEXT NOT NULL,
                        `reviewedCount` INTEGER NOT NULL,
                        `newLearnedCount` INTEGER NOT NULL,
                        `checkedInAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
