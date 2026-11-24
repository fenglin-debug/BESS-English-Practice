package com.essvocab.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        VocabPackEntity::class,
        VocabWordEntity::class,
        ReviewCardEntity::class,
        ReviewLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EssVocabDatabase : RoomDatabase() {
    abstract fun vocabDao(): VocabDao
    abstract fun reviewDao(): ReviewDao
}
