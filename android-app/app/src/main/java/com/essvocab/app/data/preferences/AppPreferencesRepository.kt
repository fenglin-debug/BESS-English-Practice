package com.essvocab.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ess_vocab_settings",
)

enum class TtsAccent(val code: String, val label: String) {
    US("US", "美式英语 (en-US)"),
    UK("UK", "英式英语 (en-GB)"),
    ;

    companion object {
        fun fromCode(code: String?): TtsAccent =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: US
    }
}

enum class AppThemeMode(val code: String, val label: String) {
    LIGHT("LIGHT", "浅色清新"),
    SYSTEM("SYSTEM", "跟随系统"),
    DARK("DARK", "深色模式"),
    ;

    companion object {
        fun fromCode(code: String?): AppThemeMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: LIGHT
    }
}

data class AppSettings(
    val onlineSyncConsentGranted: Boolean = false,
    val manifestUrl: String = DEFAULT_MANIFEST_URL,
    val datasetVersion: String = "",
    val manifestEtag: String = "",
    val lastSyncAtMillis: Long = 0L,
    val dailyNewLimit: Int = 20,
    val dailyReviewLimit: Int = 50,
    val desiredRetention: Double = 0.90,
    val autoPlayAudio: Boolean = true,
    val ttsAccent: TtsAccent = TtsAccent.US,
    val ttsSpeechRate: Float = 1.0f,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val selectedPackId: String = "",
) {
    companion object {
        const val DEFAULT_MANIFEST_URL =
            "https://cdn.jsdelivr.net/gh/fenglin-debug/BESS-English-Practice@main/vocab-repo/dist/manifest.json"
        const val GITHUB_RAW_MANIFEST_URL =
            "https://raw.githubusercontent.com/fenglin-debug/BESS-English-Practice/main/vocab-repo/dist/manifest.json"
        const val EMULATOR_LOCAL_URL = "http://10.0.2.2:8000/manifest.json"

        val OFFICIAL_MIRROR_URLS: List<String> = listOf(
            DEFAULT_MANIFEST_URL,
            "https://fastly.jsdelivr.net/gh/fenglin-debug/BESS-English-Practice@main/vocab-repo/dist/manifest.json",
            "https://gcore.jsdelivr.net/gh/fenglin-debug/BESS-English-Practice@main/vocab-repo/dist/manifest.json",
            "https://ghfast.top/https://raw.githubusercontent.com/fenglin-debug/BESS-English-Practice/main/vocab-repo/dist/manifest.json",
            "https://gh-proxy.com/https://raw.githubusercontent.com/fenglin-debug/BESS-English-Practice/main/vocab-repo/dist/manifest.json",
            GITHUB_RAW_MANIFEST_URL,
        )

        fun normalizeManifestUrl(rawUrl: String?): String {
            val trimmed = rawUrl?.trim().orEmpty()
            if (trimmed.isBlank()) return DEFAULT_MANIFEST_URL
            val pathWithoutQuery = trimmed.substringBefore('?').lowercase()
            val isInvalidRepoOrPlaceholder =
                trimmed.contains("<YOUR_GITHUB_USER>") ||
                    trimmed.contains("<你的用户名>") ||
                    pathWithoutQuery.contains("://github.com/") ||
                    pathWithoutQuery.endsWith(".git") ||
                    !pathWithoutQuery.endsWith(".json")
            return if (isInvalidRepoOrPlaceholder) DEFAULT_MANIFEST_URL else trimmed
        }
    }
}

interface StudySettingsSource {
    suspend fun getCurrentSettings(): AppSettings
}

@Singleton
class AppPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : StudySettingsSource {
    private object Keys {
        val ONLINE_SYNC_CONSENT = booleanPreferencesKey("online_sync_consent_granted")
        val MANIFEST_URL = stringPreferencesKey("manifest_url")
        val DATASET_VERSION = stringPreferencesKey("dataset_version")
        val MANIFEST_ETAG = stringPreferencesKey("manifest_etag")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val DAILY_NEW_LIMIT = intPreferencesKey("daily_new_limit")
        val DAILY_REVIEW_LIMIT = intPreferencesKey("daily_review_limit")
        val DESIRED_RETENTION = doublePreferencesKey("desired_retention")
        val AUTO_PLAY_AUDIO = booleanPreferencesKey("auto_play_audio")
        val TTS_ACCENT = stringPreferencesKey("tts_accent")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_PACK_ID = stringPreferencesKey("selected_pack_id")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val resolvedUrl = AppSettings.normalizeManifestUrl(prefs[Keys.MANIFEST_URL])
        AppSettings(
            onlineSyncConsentGranted = prefs[Keys.ONLINE_SYNC_CONSENT] ?: false,
            manifestUrl = resolvedUrl,
            datasetVersion = prefs[Keys.DATASET_VERSION] ?: "",
            manifestEtag = prefs[Keys.MANIFEST_ETAG] ?: "",
            lastSyncAtMillis = prefs[Keys.LAST_SYNC_AT] ?: 0L,
            dailyNewLimit = prefs[Keys.DAILY_NEW_LIMIT] ?: 20,
            dailyReviewLimit = prefs[Keys.DAILY_REVIEW_LIMIT] ?: 50,
            desiredRetention = prefs[Keys.DESIRED_RETENTION] ?: 0.90,
            autoPlayAudio = prefs[Keys.AUTO_PLAY_AUDIO] ?: true,
            ttsAccent = TtsAccent.fromCode(prefs[Keys.TTS_ACCENT]),
            ttsSpeechRate = prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f,
            themeMode = AppThemeMode.fromCode(prefs[Keys.THEME_MODE]),
            selectedPackId = prefs[Keys.SELECTED_PACK_ID] ?: "",
        )
    }

    override suspend fun getCurrentSettings(): AppSettings = settingsFlow.first()

    suspend fun setOnlineSyncConsent(granted: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ONLINE_SYNC_CONSENT] = granted
        }
    }

    suspend fun updateManifestUrl(url: String) {
        val cleaned = AppSettings.normalizeManifestUrl(url)
        context.settingsDataStore.edit { prefs ->
            val changed = prefs[Keys.MANIFEST_URL] != cleaned
            prefs[Keys.MANIFEST_URL] = cleaned
            if (changed) {
                prefs.remove(Keys.MANIFEST_ETAG)
            }
        }
    }

    suspend fun updateSyncMetadata(datasetVersion: String, etag: String?, syncedAtMillis: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DATASET_VERSION] = datasetVersion
            if (!etag.isNullOrBlank()) {
                prefs[Keys.MANIFEST_ETAG] = etag
            }
            prefs[Keys.LAST_SYNC_AT] = syncedAtMillis
        }
    }

    suspend fun updateDailyNewLimit(limit: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DAILY_NEW_LIMIT] = limit.coerceIn(5, 100)
        }
    }

    suspend fun updateDailyReviewLimit(limit: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DAILY_REVIEW_LIMIT] = limit.coerceIn(10, 200)
        }
    }

    suspend fun updateDesiredRetention(retention: Double) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DESIRED_RETENTION] = retention.coerceIn(0.80, 0.97)
        }
    }

    suspend fun updateAutoPlayAudio(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_PLAY_AUDIO] = enabled
        }
    }

    suspend fun updateTtsAccent(accent: TtsAccent) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TTS_ACCENT] = accent.code
        }
    }

    suspend fun updateTtsSpeechRate(rate: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.TTS_SPEECH_RATE] = rate.coerceIn(0.5f, 1.5f)
        }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.code
        }
    }

    suspend fun updateSelectedPackId(packId: String?) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SELECTED_PACK_ID] = packId.orEmpty().trim()
        }
    }
}
