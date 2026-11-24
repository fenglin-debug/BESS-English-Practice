package com.essvocab.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class VocabSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: VocabSyncRepository,
    private val syncStateStore: SyncStateStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Strict consent guard: never make network calls unless user explicitly granted consent
        if (!syncStateStore.isOnlineSyncConsentGranted()) {
            return Result.success()
        }
        return when (syncRepository.sync(forceFullCheck = false)) {
            is SyncResult.Updated,
            is SyncResult.UpToDate,
            is SyncResult.ConsentRequired,
            -> Result.success()

            is SyncResult.Error -> Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ess_vocab_periodic_sync"
    }
}
