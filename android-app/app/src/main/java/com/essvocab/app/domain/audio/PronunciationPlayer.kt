package com.essvocab.app.domain.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.speech.tts.TextToSpeech
import com.essvocab.app.data.preferences.TtsAccent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class TtsAvailability {
    INITIALIZING,
    READY,
    MISSING_ENGLISH_VOICE,
    UNAVAILABLE,
}

@Singleton
class PronunciationPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var pendingSpeak: Triple<String, TtsAccent, Float>? = null

    private val _ttsAvailability = MutableStateFlow(TtsAvailability.INITIALIZING)
    val ttsAvailability: StateFlow<TtsAvailability> = _ttsAvailability.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    val usRes = engine?.isLanguageAvailable(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    val ukRes = engine?.isLanguageAvailable(Locale.UK) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    if (usRes >= TextToSpeech.LANG_AVAILABLE || ukRes >= TextToSpeech.LANG_AVAILABLE) {
                        _ttsAvailability.value = TtsAvailability.READY
                        pendingSpeak?.let { (text, accent, rate) ->
                            pendingSpeak = null
                            speakViaTts(text, accent, rate)
                        }
                    } else {
                        _ttsAvailability.value = TtsAvailability.MISSING_ENGLISH_VOICE
                    }
                } else {
                    _ttsAvailability.value = TtsAvailability.UNAVAILABLE
                }
            }
        } catch (_: Exception) {
            _ttsAvailability.value = TtsAvailability.UNAVAILABLE
        }
    }

    /**
     * Dual-track pronunciation:
     * 1. If remote audioUrl is present, download & cache locally and play via MediaPlayer.
     * 2. Otherwise (or on download failure), use offline Android TextToSpeech.
     */
    fun speak(
        ttsText: String,
        audioUrl: String? = null,
        accent: TtsAccent = TtsAccent.US,
        speechRate: Float = 1.0f,
    ) {
        stop()
        if (!audioUrl.isNullOrBlank() && (audioUrl.startsWith("http://") || audioUrl.startsWith("https://"))) {
            scope.launch {
                val cachedFile = fetchAndCacheAudio(audioUrl)
                if (cachedFile != null && playCachedFile(cachedFile, speechRate)) {
                    return@launch
                }
                speakViaTts(ttsText, accent, speechRate)
            }
        } else {
            speakViaTts(ttsText, accent, speechRate)
        }
    }

    private fun speakViaTts(text: String, accent: TtsAccent, rate: Float) {
        val engine = tts
        if (engine == null || _ttsAvailability.value == TtsAvailability.INITIALIZING) {
            pendingSpeak = Triple(text, accent, rate)
            return
        }
        if (_ttsAvailability.value != TtsAvailability.READY) {
            return
        }
        val locale = if (accent == TtsAccent.UK) Locale.UK else Locale.US
        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        engine.setSpeechRate(rate.coerceIn(0.5f, 1.5f))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ess_term_${System.currentTimeMillis()}")
    }

    private suspend fun fetchAndCacheAudio(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "audio_cache").apply { mkdirs() }
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(24)
            val file = File(cacheDir, "$hash.audio")
            if (file.exists() && file.length() > 0L) {
                return@withContext file
            }
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                file.writeBytes(bytes)
                file
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun playCachedFile(file: File, speechRate: Float): Boolean {
        return try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                setDataSource(file.absolutePath)
                prepare()
                playbackParams = PlaybackParams().setSpeed(speechRate.coerceIn(0.5f, 1.5f))
                setOnCompletionListener { mp -> mp.release() }
                start()
            }
            mediaPlayer = player
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
        } catch (_: Exception) {
            mediaPlayer = null
        }
    }
}
