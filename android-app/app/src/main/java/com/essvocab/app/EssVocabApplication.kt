package com.essvocab.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.data.sync.VocabSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EssVocabApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var syncScheduler: VocabSyncScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Only register periodic background sync if the user has already granted consent.
        // Zero network calls or scheduled workers occur prior to user consent.
        appScope.launch {
            if (preferencesRepository.getCurrentSettings().onlineSyncConsentGranted) {
                syncScheduler.enablePeriodicSync()
            }
        }
    }
}
