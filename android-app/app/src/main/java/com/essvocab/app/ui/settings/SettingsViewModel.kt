package com.essvocab.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.data.preferences.AppSettings
import com.essvocab.app.data.preferences.TtsAccent
import com.essvocab.app.data.sync.SyncResult
import com.essvocab.app.data.sync.VocabSyncRepository
import com.essvocab.app.data.sync.VocabSyncScheduler
import com.essvocab.app.domain.audio.PronunciationPlayer
import com.essvocab.app.domain.audio.TtsAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val urlInput: String = AppSettings.DEFAULT_MANIFEST_URL,
    val isBusy: Boolean = false,
    val showConsentDialog: Boolean = false,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
    val ttsAvailability: TtsAvailability = TtsAvailability.INITIALIZING,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
    private val syncRepository: VocabSyncRepository,
    private val syncScheduler: VocabSyncScheduler,
    private val pronunciationPlayer: PronunciationPlayer,
) : ViewModel() {

    private val urlInputFlow = MutableStateFlow<String?>(null)
    private val actionStatusFlow = MutableStateFlow(ActionStatus())
    private var pendingAction: PendingNetworkAction? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settingsFlow,
        urlInputFlow,
        actionStatusFlow,
        pronunciationPlayer.ttsAvailability,
    ) { settings, customInput, status, tts ->
        SettingsUiState(
            settings = settings,
            urlInput = customInput ?: settings.manifestUrl,
            isBusy = status.isBusy,
            showConsentDialog = status.showConsentDialog,
            statusMessage = status.message,
            statusIsError = status.isError,
            ttsAvailability = tts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun onUrlInputChange(newUrl: String) {
        urlInputFlow.value = newUrl
    }

    fun applyPresetUrl(presetUrl: String) {
        urlInputFlow.value = presetUrl
    }

    fun setOnlineSyncConsent(granted: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOnlineSyncConsent(granted)
            if (granted) {
                syncScheduler.enablePeriodicSync()
                executeSync(forceFullCheck = false)
            } else {
                syncScheduler.cancelPeriodicSync()
                actionStatusFlow.value = ActionStatus(
                    message = "已关闭自动联网更新，应用将保持纯离线状态。",
                    isError = false,
                )
            }
        }
    }

    fun checkUpdateNow() {
        if (actionStatusFlow.value.isBusy) return
        viewModelScope.launch {
            if (!preferencesRepository.getCurrentSettings().onlineSyncConsentGranted) {
                pendingAction = PendingNetworkAction.SYNC
                actionStatusFlow.value = actionStatusFlow.value.copy(showConsentDialog = true)
                return@launch
            }
            executeSync(forceFullCheck = false)
        }
    }

    fun testConnection() {
        if (actionStatusFlow.value.isBusy) return
        viewModelScope.launch {
            if (!preferencesRepository.getCurrentSettings().onlineSyncConsentGranted) {
                pendingAction = PendingNetworkAction.TEST
                actionStatusFlow.value = actionStatusFlow.value.copy(showConsentDialog = true)
                return@launch
            }
            executeTestConnection()
        }
    }

    fun saveUrlAndSync(forceFullCheck: Boolean = false) {
        if (actionStatusFlow.value.isBusy) return
        viewModelScope.launch {
            val url = uiState.value.urlInput.trim()
            preferencesRepository.updateManifestUrl(url)
            urlInputFlow.value = null

            if (!preferencesRepository.getCurrentSettings().onlineSyncConsentGranted) {
                pendingAction = if (forceFullCheck) PendingNetworkAction.FORCE_SYNC else PendingNetworkAction.SYNC
                actionStatusFlow.value = actionStatusFlow.value.copy(showConsentDialog = true)
                return@launch
            }
            executeSync(forceFullCheck)
        }
    }

    fun confirmConsentAndContinue() {
        viewModelScope.launch {
            preferencesRepository.setOnlineSyncConsent(true)
            syncScheduler.enablePeriodicSync()
            actionStatusFlow.value = actionStatusFlow.value.copy(showConsentDialog = false)
            when (pendingAction) {
                PendingNetworkAction.TEST -> executeTestConnection()
                PendingNetworkAction.SYNC -> executeSync(forceFullCheck = false)
                PendingNetworkAction.FORCE_SYNC -> executeSync(forceFullCheck = true)
                null -> Unit
            }
            pendingAction = null
        }
    }

    fun dismissConsentDialog() {
        pendingAction = null
        actionStatusFlow.value = actionStatusFlow.value.copy(
            showConsentDialog = false,
            message = "未授权联网更新，已取消本次网络请求。",
            isError = false,
        )
    }

    private suspend fun executeTestConnection() {
        val url = uiState.value.urlInput.trim()
        actionStatusFlow.value = ActionStatus(isBusy = true, message = "正在测试连接...")
        when (val res = syncRepository.testConnection(url)) {
            is SyncResult.ConsentRequired -> {
                actionStatusFlow.value = ActionStatus(isBusy = false, showConsentDialog = true)
            }

            is SyncResult.UpToDate -> {
                actionStatusFlow.value = ActionStatus(
                    isBusy = false,
                    message = "连接成功！检测到远程词库 v${res.datasetVersion}（共 ${res.totalWords} 条词汇）",
                    isError = false,
                )
            }

            is SyncResult.Error -> {
                actionStatusFlow.value = ActionStatus(
                    isBusy = false,
                    message = res.message,
                    isError = true,
                )
            }

            is SyncResult.Updated -> Unit
        }
    }

    private suspend fun executeSync(forceFullCheck: Boolean) {
        actionStatusFlow.value = ActionStatus(isBusy = true, message = "正在拉取远程词库...")
        when (val res = syncRepository.sync(forceFullCheck = forceFullCheck)) {
            is SyncResult.ConsentRequired -> {
                actionStatusFlow.value = ActionStatus(isBusy = false, showConsentDialog = true)
            }

            is SyncResult.Updated -> {
                actionStatusFlow.value = ActionStatus(
                    isBusy = false,
                    message = "同步成功！版本 v${res.datasetVersion}，更新 ${res.updatedPacks} 个分类，当前共 ${res.totalWords} 词",
                    isError = false,
                )
            }

            is SyncResult.UpToDate -> {
                actionStatusFlow.value = ActionStatus(
                    isBusy = false,
                    message = "词库已是最新（共 ${res.totalWords} 词）",
                    isError = false,
                )
            }

            is SyncResult.Error -> {
                actionStatusFlow.value = ActionStatus(
                    isBusy = false,
                    message = res.message,
                    isError = true,
                )
            }
        }
    }

    fun updateDailyNewLimit(limit: Int) {
        viewModelScope.launch { preferencesRepository.updateDailyNewLimit(limit) }
    }

    fun updateDesiredRetention(retention: Double) {
        viewModelScope.launch { preferencesRepository.updateDesiredRetention(retention) }
    }

    fun updateAutoPlayAudio(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateAutoPlayAudio(enabled) }
    }

    fun updateTtsAccent(accent: TtsAccent) {
        viewModelScope.launch { preferencesRepository.updateTtsAccent(accent) }
    }

    fun updateTtsSpeechRate(rate: Float) {
        viewModelScope.launch { preferencesRepository.updateTtsSpeechRate(rate) }
    }

    fun testPronunciation() {
        val s = uiState.value.settings
        pronunciationPlayer.speak(
            ttsText = "Battery energy storage system",
            audioUrl = null,
            accent = s.ttsAccent,
            speechRate = s.ttsSpeechRate,
        )
    }

    private enum class PendingNetworkAction { TEST, SYNC, FORCE_SYNC }

    private data class ActionStatus(
        val isBusy: Boolean = false,
        val showConsentDialog: Boolean = false,
        val message: String? = null,
        val isError: Boolean = false,
    )
}
