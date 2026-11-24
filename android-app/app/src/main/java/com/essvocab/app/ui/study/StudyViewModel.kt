package com.essvocab.app.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essvocab.app.data.StudyRepository
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.domain.audio.PronunciationPlayer
import com.essvocab.app.domain.audio.TtsAvailability
import com.essvocab.app.domain.fsrs.FsrsRating
import com.essvocab.app.domain.fsrs.FsrsScheduleOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudyUiState(
    val isLoading: Boolean = true,
    val packTitle: String = "全库 FSRS 复习",
    val currentWord: WordWithReview? = null,
    val isRevealed: Boolean = false,
    val ratingPreviews: Map<FsrsRating, FsrsScheduleOutcome> = emptyMap(),
    val remainingCount: Int = 0,
    val completedCount: Int = 0,
    val goodOrEasyCount: Int = 0,
    val sessionFinished: Boolean = false,
    val ttsAvailability: TtsAvailability = TtsAvailability.INITIALIZING,
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studyRepository: StudyRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val pronunciationPlayer: PronunciationPlayer,
) : ViewModel() {

    private val packId: String? = savedStateHandle.get<String>("packId")?.takeIf { it.isNotBlank() && it != "all" }

    private val queue = ArrayDeque<WordWithReview>()
    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            pronunciationPlayer.ttsAvailability.collect { status ->
                _uiState.value = _uiState.value.copy(ttsAvailability = status)
            }
        }
        loadSession()
    }

    fun loadSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, sessionFinished = false)
            queue.clear()
            val items = studyRepository.buildStudyQueue(packId = packId)
            queue.addAll(items)

            val title = if (packId != null && items.isNotEmpty()) {
                items.first().packName
            } else if (packId != null) {
                "专题闪卡复习"
            } else {
                "全库 FSRS 复习"
            }

            if (queue.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    packTitle = title,
                    currentWord = null,
                    remainingCount = 0,
                    sessionFinished = true,
                )
            } else {
                showNextCard(titleOverride = title)
            }
        }
    }

    private suspend fun showNextCard(titleOverride: String? = null) {
        val next = queue.firstOrNull()
        if (next == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentWord = null,
                isRevealed = false,
                ratingPreviews = emptyMap(),
                remainingCount = 0,
                sessionFinished = true,
            )
            return
        }

        val previews = studyRepository.previewRatings(next)
        val settings = preferencesRepository.getCurrentSettings()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            packTitle = titleOverride ?: _uiState.value.packTitle,
            currentWord = next,
            isRevealed = false,
            ratingPreviews = previews,
            remainingCount = queue.size,
            sessionFinished = false,
        )

        if (settings.autoPlayAudio) {
            pronunciationPlayer.speak(
                ttsText = next.word.ttsText,
                audioUrl = next.word.audioUrl,
                accent = settings.ttsAccent,
                speechRate = settings.ttsSpeechRate,
            )
        }
    }

    fun revealAnswer() {
        _uiState.value = _uiState.value.copy(isRevealed = true)
    }

    fun speakCurrent(slowMode: Boolean = false) {
        val current = _uiState.value.currentWord ?: return
        viewModelScope.launch {
            val settings = preferencesRepository.getCurrentSettings()
            val rate = if (slowMode) 0.70f else settings.ttsSpeechRate
            pronunciationPlayer.speak(
                ttsText = current.word.ttsText,
                audioUrl = current.word.audioUrl,
                accent = settings.ttsAccent,
                speechRate = rate,
            )
        }
    }

    fun submitRating(rating: FsrsRating) {
        val current = queue.removeFirstOrNull() ?: return
        viewModelScope.launch {
            val outcome = studyRepository.submitReview(current, rating)
            val updatedItem = current.copy(card = outcome.updatedCard)

            // Re-queue short-term forgotten/hard cards within this session for reinforcement
            if (rating == FsrsRating.AGAIN) {
                val insertPos = minOf(2, queue.size)
                queue.add(insertPos, updatedItem)
            } else if (rating == FsrsRating.HARD && outcome.updatedCard.scheduledDays == 0) {
                val insertPos = minOf(4, queue.size)
                queue.add(insertPos, updatedItem)
            }

            val newCompleted = _uiState.value.completedCount + 1
            val newGoodOrEasy = _uiState.value.goodOrEasyCount +
                if (rating == FsrsRating.GOOD || rating == FsrsRating.EASY) 1 else 0

            _uiState.value = _uiState.value.copy(
                completedCount = newCompleted,
                goodOrEasyCount = newGoodOrEasy,
            )
            showNextCard()
        }
    }

    override fun onCleared() {
        super.onCleared()
        pronunciationPlayer.stop()
    }
}
