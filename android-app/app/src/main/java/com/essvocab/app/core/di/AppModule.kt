package com.essvocab.app.core.di

import android.content.Context
import androidx.room.Room
import com.essvocab.app.data.local.EssVocabDatabase
import com.essvocab.app.data.local.ReviewDao
import com.essvocab.app.data.local.VocabDao
import com.essvocab.app.data.sync.PreferencesSyncStateStore
import com.essvocab.app.data.sync.SyncStateStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EssVocabDatabase {
        return Room.databaseBuilder(
            context,
            EssVocabDatabase::class.java,
            "ess_vocab.db",
        )
            .addMigrations(EssVocabDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideVocabDao(database: EssVocabDatabase): VocabDao = database.vocabDao()

    @Provides
    fun provideReviewDao(database: EssVocabDatabase): ReviewDao = database.reviewDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSyncStateStore(impl: PreferencesSyncStateStore): SyncStateStore
}
