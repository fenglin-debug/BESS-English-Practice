package com.essvocab.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essvocab.app.data.BookStats
import com.essvocab.app.data.StudyRepository
import com.essvocab.app.data.local.PackSummaryRow
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.data.preferences.AppSettings
import com.essvocab.app.data.sync.SyncResult
import com.essvocab.app.data.sync.VocabSyncRepository
import com.essvocab.app.data.sync.VocabSyncScheduler
import com.essvocab.app.domain.shanbay.ShanbayMechanics
import com.essvocab.app.domain.shanbay.StreakSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val totalActiveWords: Int = 0,
    val selectedBookName: String = "储能行业全套词汇",
    val bookTotalWords: Int = 0,
    val bookMasteredWords: Int = 0,
    val todayNewRemaining: Int = 0,
    val todayReviewRemaining: Int = 0,
    val estimatedDaysLeft: Int = 0,
    val notebookCount: Int = 0,
    val streakSummary: StreakSummary = ShanbayMechanics.calculateStreak(emptyList()),
    val isSyncing: Boolean = false,
    val showConsentDialog: Boolean = false,
    val syncBannerMessage: String? = null,
    val syncBannerIsError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val bookStatsFlow = preferencesRepository.settingsFlow
        .map { it.selectedPackId }
        .distinctUntilChanged()
        .flatMapLatest { packId ->
            studyRepository.observeBookStats(packId)
        }

    private val repoMetaFlow = combine(
        studyRepository.observeTotalActiveWords(),
        studyRepository.observePackSummaries(),
        studyRepository.observeNotebookCount(),
        studyRepository.observeCheckInStreak(),
    ) { totalActive, packs, notebookCount, streak ->
        RepoMeta(totalActive, packs, notebookCount, streak)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        preferencesRepository.settingsFlow,
        bookStatsFlow,
        repoMetaFlow,
        syncState,
    ) { settings, bookStats, meta, sync ->
        val selectedPackName = if (settings.selectedPackId.isBlank()) {
            "储能行业全套词汇"
        } else {
            meta.packs.firstOrNull { it.id == settings.selectedPackId }?.name ?: "储能行业全套词汇"
        }

        val dailyNewRemaining = min(
            bookStats.unseenWords,
            max(0, settings.dailyNewLimit - bookStats.todayNewLearned),
        )
        val remainingToMaster = (bookStats.totalWords - bookStats.masteredWords).coerceAtLeast(0)
        val estimatedDays = if (remainingToMaster == 0) {
            0
        } else {
            ceil(remainingToMaster.toDouble() / settings.dailyNewLimit.coerceAtLeast(1)).toInt()
        }

        HomeUiState(
            settings = settings,
            totalActiveWords = meta.totalActive,
            selectedBookName = selectedPackName,
            bookTotalWords = bookStats.totalWords,
            bookMasteredWords = bookStats.masteredWords,
            todayNewRemaining = dailyNewRemaining,
            todayReviewRemaining = bookStats.dueWords,
            estimatedDaysLeft = estimatedDays,
            notebookCount = meta.notebookCount,
            streakSummary = meta.streak,
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

    private data class RepoMeta(
        val totalActive: Int,
        val packs: List<PackSummaryRow>,
        val notebookCount: Int,
        val streak: StreakSummary,
    )

    private data class SyncUiStatus(
        val isSyncing: Boolean = false,
        val showConsentDialog: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false,
    )
}
