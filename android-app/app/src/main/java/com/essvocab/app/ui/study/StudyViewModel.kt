package com.essvocab.app.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essvocab.app.data.StudyMode
import com.essvocab.app.data.StudyRepository
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.domain.audio.PronunciationPlayer
import com.essvocab.app.domain.audio.TtsAvailability
import com.essvocab.app.domain.fsrs.FsrsRating
import com.essvocab.app.domain.fsrs.FsrsScheduleOutcome
import com.essvocab.app.domain.shanbay.ShanbayMechanics
import com.essvocab.app.domain.shanbay.StreakSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StudyStage {
    RECOGNIZE,
    DETAIL,
    COMPLETED,
}

enum class InitialVerdict {
    NONE,
    KNOWN,
    VAGUE,
    UNKNOWN,
}

data class StudyUiState(
    val isLoading: Boolean = true,
    val sessionTitle: String = "今日学习",
    val stage: StudyStage = StudyStage.RECOGNIZE,
    val currentWord: WordWithReview? = null,
    val hintLevel: Int = 0,
    val hintText: String = "",
    val initialVerdict: InitialVerdict = InitialVerdict.NONE,
    val ratingPreviews: Map<FsrsRating, FsrsScheduleOutcome> = emptyMap(),
    val isFavorite: Boolean = false,
    val remainingCount: Int = 0,
    val completedCount: Int = 0,
    val goodOrEasyCount: Int = 0,
    val slashedCount: Int = 0,
    val streakSummary: StreakSummary = ShanbayMechanics.calculateStreak(emptyList()),
    val ttsAvailability: TtsAvailability = TtsAvailability.INITIALIZING,
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val studyRepository: StudyRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val pronunciationPlayer: PronunciationPlayer,
) : ViewModel() {

    private val rawMode: String = savedStateHandle.get<String>("mode") ?: "book"

    private val queue = ArrayDeque<WordWithReview>()
    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            pronunciationPlayer.ttsAvailability.collect { status ->
                _uiState.value = _uiState.value.copy(ttsAvailability = status)
            }
        }
        viewModelScope.launch {
            studyRepository.observeCheckInStreak().collect { streak ->
                _uiState.value = _uiState.value.copy(streakSummary = streak)
            }
        }
        loadSession(overrideExtra = rawMode == "extra")
    }

    fun loadSession(overrideExtra: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                completedCount = 0,
                goodOrEasyCount = 0,
                slashedCount = 0,
            )
            queue.clear()

            val settings = preferencesRepository.getCurrentSettings()
            val (mode, scopedPackId, fallbackTitle) = when {
                overrideExtra -> Triple(
                    StudyMode.EXTRA_PRACTICE,
                    settings.selectedPackId.takeIf { it.isNotBlank() },
                    "巩固加练",
                )

                rawMode == "notebook" -> Triple(StudyMode.NOTEBOOK, null, "生词本专项攻克")
                rawMode == "pack:all" -> Triple(StudyMode.DAILY_BOOK, null, "全套词书学习")
                rawMode.startsWith("pack:") -> {
                    val id = rawMode.removePrefix("pack:").takeIf { it.isNotBlank() }
                    Triple(StudyMode.DAILY_BOOK, id, "专题词书学习")
                }

                else -> Triple(
                    StudyMode.DAILY_BOOK,
                    settings.selectedPackId.takeIf { it.isNotBlank() },
                    "今日词书学习",
                )
            }

            val items = studyRepository.buildStudyQueue(mode = mode, packId = scopedPackId)
            queue.addAll(items)

            val resolvedTitle = when {
                rawMode.startsWith("pack:") && items.isNotEmpty() -> items.first().packName
                else -> fallbackTitle
            }

            if (queue.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    sessionTitle = resolvedTitle,
                    stage = StudyStage.COMPLETED,
                    currentWord = null,
                    remainingCount = 0,
                )
            } else {
                showNextCard(titleOverride = resolvedTitle)
            }
        }
    }

    private suspend fun showNextCard(titleOverride: String? = null) {
        val next = queue.firstOrNull()
        if (next == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                stage = StudyStage.COMPLETED,
                currentWord = null,
                hintLevel = 0,
                hintText = "",
                initialVerdict = InitialVerdict.NONE,
                ratingPreviews = emptyMap(),
                remainingCount = 0,
            )
            return
        }

        val previews = studyRepository.previewRatings(next)
        val settings = preferencesRepository.getCurrentSettings()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            sessionTitle = titleOverride ?: _uiState.value.sessionTitle,
            stage = StudyStage.RECOGNIZE,
            currentWord = next,
            hintLevel = 0,
            hintText = "",
            initialVerdict = InitialVerdict.NONE,
            ratingPreviews = previews,
            isFavorite = next.card?.isFavorite ?: false,
            remainingCount = queue.size,
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

    fun onChooseKnown() {
        if (_uiState.value.currentWord == null) return
        _uiState.value = _uiState.value.copy(
            stage = StudyStage.DETAIL,
            initialVerdict = InitialVerdict.KNOWN,
        )
    }

    fun onChooseVague() {
        val current = _uiState.value.currentWord ?: return
        if (_uiState.value.hintLevel == 0) {
            val hint = ShanbayMechanics.buildProgressiveHint(
                zh = current.word.zh,
                packName = current.packName,
                abbreviations = current.word.abbreviationsList(),
            )
            _uiState.value = _uiState.value.copy(
                hintLevel = 1,
                hintText = hint,
            )
        } else {
            _uiState.value = _uiState.value.copy(
                stage = StudyStage.DETAIL,
                initialVerdict = InitialVerdict.VAGUE,
            )
        }
    }

    fun onConfirmHintRecalled() {
        if (_uiState.value.currentWord == null) return
        _uiState.value = _uiState.value.copy(
            stage = StudyStage.DETAIL,
            initialVerdict = InitialVerdict.VAGUE,
        )
    }

    fun onChooseUnknown() {
        if (_uiState.value.currentWord == null) return
        _uiState.value = _uiState.value.copy(
            stage = StudyStage.DETAIL,
            initialVerdict = InitialVerdict.UNKNOWN,
        )
    }

    fun onUndoKnownToUnknown() {
        commitAndAdvance(rating = FsrsRating.AGAIN, requeueOffset = 2)
    }

    fun onProceedNext() {
        when (_uiState.value.initialVerdict) {
            InitialVerdict.KNOWN -> commitAndAdvance(rating = FsrsRating.GOOD, requeueOffset = null)
            InitialVerdict.VAGUE -> commitAndAdvance(rating = FsrsRating.HARD, requeueOffset = 4)
            InitialVerdict.UNKNOWN,
            InitialVerdict.NONE,
            -> commitAndAdvance(rating = FsrsRating.AGAIN, requeueOffset = 2)
        }
    }

    private fun commitAndAdvance(rating: FsrsRating, requeueOffset: Int?) {
        val current = queue.removeFirstOrNull() ?: return
        viewModelScope.launch {
            val outcome = studyRepository.submitReview(current, rating)
            val updatedItem = current.copy(card = outcome.updatedCard)

            val shouldRequeue = when {
                rating == FsrsRating.AGAIN && requeueOffset != null -> true
                rating == FsrsRating.HARD && requeueOffset != null && outcome.updatedCard.scheduledDays == 0 -> true
                else -> false
            }
            if (shouldRequeue && requeueOffset != null) {
                val insertPos = minOf(requeueOffset, queue.size)
                queue.add(insertPos, updatedItem)
            }

            _uiState.value = _uiState.value.copy(
                completedCount = _uiState.value.completedCount + 1,
                goodOrEasyCount = _uiState.value.goodOrEasyCount +
                    if (rating == FsrsRating.GOOD || rating == FsrsRating.EASY) 1 else 0,
            )
            showNextCard()
        }
    }

    fun onSlashTooEasy() {
        val current = queue.removeFirstOrNull() ?: return
        viewModelScope.launch {
            studyRepository.markTooEasy(current)
            _uiState.value = _uiState.value.copy(
                completedCount = _uiState.value.completedCount + 1,
                goodOrEasyCount = _uiState.value.goodOrEasyCount + 1,
                slashedCount = _uiState.value.slashedCount + 1,
            )
            showNextCard()
        }
    }

    fun onToggleFavorite() {
        val current = _uiState.value.currentWord ?: return
        viewModelScope.launch {
            val newFav = studyRepository.toggleFavorite(current.word.id)
            val updatedCard = current.card?.copy(isFavorite = newFav)
            val updatedWord = current.copy(card = updatedCard)
            if (queue.isNotEmpty() && queue.first().word.id == current.word.id) {
                queue[0] = updatedWord
            }
            _uiState.value = _uiState.value.copy(
                currentWord = updatedWord,
                isFavorite = newFav,
            )
        }
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

    fun onCompleteCheckIn() {
        viewModelScope.launch {
            studyRepository.recordTodayCheckIn()
        }
    }

    fun onStartExtraRound() {
        loadSession(overrideExtra = true)
    }

    override fun onCleared() {
        super.onCleared()
        pronunciationPlayer.stop()
    }
}
