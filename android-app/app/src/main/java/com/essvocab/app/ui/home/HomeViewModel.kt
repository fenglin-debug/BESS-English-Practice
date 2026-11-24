package com.essvocab.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essvocab.app.data.StudyRepository
import com.essvocab.app.data.local.PackSummaryRow
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.data.preferences.AppSettings
import com.essvocab.app.data.sync.SyncResult
import com.essvocab.app.data.sync.VocabSyncRepository
import com.essvocab.app.data.sync.VocabSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val packs: List<PackSummaryRow> = emptyList(),
    val totalWords: Int = 0,
    val dueCount: Int = 0,
    val unseenCount: Int = 0,
    val todayReviewedCount: Int = 0,
    val masteredCount: Int = 0,
    val isSyncing: Boolean = false,
    val showConsentDialog: Boolean = false,
    val syncBannerMessage: String? = null,
    val syncBannerIsError: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val syncRepository: VocabSyncRepository,
    private val syncScheduler: VocabSyncScheduler,
) : ViewModel() {

    private val syncState = MutableStateFlow(SyncUiStatus())
    private var pendingForceFullCheck = false
    private var hasHandledStartupCheck = false

    private val statsFlow = combine(
        studyRepository.observeTotalActiveWords(),
        studyRepository.observeDueCount(),
        studyRepository.observeUnseenCount(),
        studyRepository.observeTodayReviewedCount(),
        studyRepository.observeMasteredCount(),
    ) { total, due, unseen, today, mastered ->
        HomeStats(total, due, unseen, today, mastered)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        preferencesRepository.settingsFlow,
        studyRepository.observePackSummaries(),
        statsFlow,
        syncState,
    ) { settings, packs, stats, sync ->
        HomeUiState(
            settings = settings,
            packs = packs,
            totalWords = stats.totalWords,
            dueCount = stats.dueCount,
            unseenCount = stats.unseenCount,
            todayReviewedCount = stats.todayReviewedCount,
            masteredCount = stats.masteredCount,
            isSyncing = sync.isSyncing,
            showConsentDialog = sync.showConsentDialog,
            syncBannerMessage = sync.message,
            syncBannerIsError = sync.isError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        runStartupAutoFlow()
    }

    /**
     * Consumer-ready launch behavior:
     * - First launch (not consented & empty DB): immediately prompts the one-time consent dialog.
     * - Subsequent launches (consented): proactively checks for updates in the background silently.
     */
    private fun runStartupAutoFlow() {
        if (hasHandledStartupCheck) return
        hasHandledStartupCheck = true
        viewModelScope.launch {
            val settings = preferencesRepository.getCurrentSettings()
            val cachedWords = studyRepository.observeTotalActiveWords().first()
            if (!settings.onlineSyncConsentGranted) {
                if (cachedWords == 0) {
                    syncState.value = syncState.value.copy(showConsentDialog = true)
                }
            } else {
                syncScheduler.enablePeriodicSync()
                executeSync(forceFullCheck = false, silentWhenUpToDateOrOffline = cachedWords > 0)
            }
        }
    }

    fun requestSync(forceFullCheck: Boolean = false) {
        if (syncState.value.isSyncing) return
        viewModelScope.launch {
            val settings = preferencesRepository.getCurrentSettings()
            if (!settings.onlineSyncConsentGranted) {
                pendingForceFullCheck = forceFullCheck
                syncState.value = syncState.value.copy(showConsentDialog = true)
            } else {
                executeSync(forceFullCheck = forceFullCheck, silentWhenUpToDateOrOffline = false)
            }
        }
    }

    fun confirmConsentAndSync(forceFullCheck: Boolean = pendingForceFullCheck) {
        viewModelScope.launch {
            preferencesRepository.setOnlineSyncConsent(true)
            syncScheduler.enablePeriodicSync()
            syncState.value = syncState.value.copy(showConsentDialog = false)
            executeSync(forceFullCheck = forceFullCheck, silentWhenUpToDateOrOffline = false)
        }
    }

    fun declineConsentDialog() {
        syncState.value = syncState.value.copy(
            showConsentDialog = false,
            message = null,
            isError = false,
        )
    }

    private suspend fun executeSync(
        forceFullCheck: Boolean,
        silentWhenUpToDateOrOffline: Boolean,
    ) {
        syncState.value = SyncUiStatus(isSyncing = true)
        when (val result = syncRepository.sync(forceFullCheck = forceFullCheck)) {
            is SyncResult.ConsentRequired -> {
                syncState.value = SyncUiStatus(
                    isSyncing = false,
                    showConsentDialog = true,
                )
            }

            is SyncResult.Updated -> {
                val msg = if (silentWhenUpToDateOrOffline) {
                    "词库已自动更新至 v${result.datasetVersion}（新增/更新 ${result.updatedPacks} 个专题，共 ${result.totalWords} 词）"
                } else {
                    "已同步最新词库 v${result.datasetVersion}（共 ${result.totalWords} 条储能术语）"
                }
                syncState.value = SyncUiStatus(
                    isSyncing = false,
                    message = msg,
                    isError = false,
                )
            }

            is SyncResult.UpToDate -> {
                if (silentWhenUpToDateOrOffline) {
                    syncState.value = SyncUiStatus(isSyncing = false)
                } else {
                    val verLabel = if (result.datasetVersion.isNotBlank()) " v${result.datasetVersion}" else ""
                    syncState.value = SyncUiStatus(
                        isSyncing = false,
                        message = "词库已是最新版本$verLabel（共 ${result.totalWords} 词）",
                        isError = false,
                    )
                }
            }

            is SyncResult.Error -> {
                if (silentWhenUpToDateOrOffline) {
                    // Stay unobtrusive when offline and user already has local cached words
                    syncState.value = SyncUiStatus(isSyncing = false)
                } else {
                    syncState.value = SyncUiStatus(
                        isSyncing = false,
                        message = result.message,
                        isError = true,
                    )
                }
            }
        }
    }

    fun dismissBanner() {
        syncState.value = syncState.value.copy(message = null, isError = false)
    }

    private data class HomeStats(
        val totalWords: Int,
        val dueCount: Int,
        val unseenCount: Int,
        val todayReviewedCount: Int,
        val masteredCount: Int,
    )

    private data class SyncUiStatus(
        val isSyncing: Boolean = false,
        val showConsentDialog: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false,
    )
}
