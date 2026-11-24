package com.essvocab.app.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essvocab.app.data.StudyMode
import com.essvocab.app.data.StudyRepository
import com.essvocab.app.data.local.ReviewCardEntity
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
    val maxHintLevel: Int = 1,
    val hintText: String = "",
    val initialVerdict: InitialVerdict = InitialVerdict.NONE,
    val ratingPreviews: Map<FsrsRating, FsrsScheduleOutcome> = emptyMap(),
    val isFavorite: Boolean = false,
    val remainingCount: Int = 0,
    val completedCount: Int = 0,
    val goodOrEasyCount: Int = 0,
    val slashedCount: Int = 0,
    val canUndo: Boolean = false,
    val feedbackMessage: String? = null,
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

    private data class UndoEntry(
        val queueSnapshot: List<WordWithReview>,
        val wordId: String,
        val previousCard: ReviewCardEntity?,
        val logId: Long,
        val completedCount: Int,
        val goodOrEasyCount: Int,
        val slashedCount: Int,
    )

    private val rawMode: String = savedStateHandle.get<String>("mode") ?: "book"

    private val queue = ArrayDeque<WordWithReview>()
    private val undoStack = ArrayDeque<UndoEntry>()
    private var isCommitting = false

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
            undoStack.clear()
            isCommitting = false
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                completedCount = 0,
                goodOrEasyCount = 0,
                slashedCount = 0,
                canUndo = false,
                feedbackMessage = null,
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
                    canUndo = false,
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
                maxHintLevel = 1,
                hintText = "",
                initialVerdict = InitialVerdict.NONE,
                ratingPreviews = emptyMap(),
                remainingCount = 0,
                canUndo = undoStack.isNotEmpty(),
            )
            return
        }

        val previews = studyRepository.previewRatings(next)
        val settings = preferencesRepository.getCurrentSettings()
        val maxLevel = ShanbayMechanics.maxHintLevel(next.word.exampleEn)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            sessionTitle = titleOverride ?: _uiState.value.sessionTitle,
            stage = StudyStage.RECOGNIZE,
            currentWord = next,
            hintLevel = 0,
            maxHintLevel = maxLevel,
            hintText = "",
            initialVerdict = InitialVerdict.NONE,
            ratingPreviews = previews,
            isFavorite = next.card?.isFavorite ?: false,
            remainingCount = queue.size,
            canUndo = undoStack.isNotEmpty(),
        )

        pronunciationPlayer.prefetch(
            audioUrls = queue.take(4).mapNotNull { it.word.audioUrl },
            allowNetwork = settings.onlineSyncConsentGranted,
        )

        if (settings.autoPlayAudio) {
            pronunciationPlayer.speak(
                ttsText = next.word.ttsText,
                audioUrl = next.word.audioUrl,
                accent = settings.ttsAccent,
                speechRate = settings.ttsSpeechRate,
                allowNetworkAudio = settings.onlineSyncConsentGranted,
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
        val maxLevel = ShanbayMechanics.maxHintLevel(current.word.exampleEn)
        val nextHintLevel = _uiState.value.hintLevel + 1
        if (nextHintLevel <= maxLevel) {
            val hint = ShanbayMechanics.buildProgressiveHint(
                zh = current.word.zh,
                packName = current.packName,
                abbreviations = current.word.abbreviationsList(),
                exampleEn = current.word.exampleEn,
                hintLevel = nextHintLevel,
            )
            _uiState.value = _uiState.value.copy(
                hintLevel = nextHintLevel,
                maxHintLevel = maxLevel,
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

    private fun pushUndo(entry: UndoEntry) {
        if (undoStack.size >= MAX_UNDO_STEPS) {
            undoStack.removeFirst()
        }
        undoStack.addLast(entry)
    }

    private fun commitAndAdvance(rating: FsrsRating, requeueOffset: Int?) {
        if (isCommitting) return
        val current = queue.firstOrNull() ?: return
        isCommitting = true
        val queueSnapshot = queue.toList()
        val prevCompleted = _uiState.value.completedCount
        val prevGoodOrEasy = _uiState.value.goodOrEasyCount
        val prevSlashed = _uiState.value.slashedCount

        viewModelScope.launch {
            try {
                queue.removeFirstOrNull()
                val recorded = studyRepository.submitReview(current, rating)
                pushUndo(
                    UndoEntry(
                        queueSnapshot = queueSnapshot,
                        wordId = current.word.id,
                        previousCard = recorded.previousCard,
                        logId = recorded.logId,
                        completedCount = prevCompleted,
                        goodOrEasyCount = prevGoodOrEasy,
                        slashedCount = prevSlashed,
                    ),
                )

                val updatedItem = current.copy(card = recorded.outcome.updatedCard)
                val shouldRequeue = when {
                    rating == FsrsRating.AGAIN && requeueOffset != null -> true
                    rating == FsrsRating.HARD && requeueOffset != null && recorded.outcome.updatedCard.scheduledDays == 0 -> true
                    else -> false
                }
                if (shouldRequeue && requeueOffset != null) {
                    val insertPos = minOf(requeueOffset, queue.size)
                    queue.add(insertPos, updatedItem)
                }

                _uiState.value = _uiState.value.copy(
                    completedCount = prevCompleted + 1,
                    goodOrEasyCount = prevGoodOrEasy +
                        if (rating == FsrsRating.GOOD || rating == FsrsRating.EASY) 1 else 0,
                    canUndo = undoStack.isNotEmpty(),
                )
                showNextCard()
            } finally {
                isCommitting = false
            }
        }
    }

    fun onSlashTooEasy() {
        if (isCommitting) return
        val current = queue.firstOrNull() ?: return
        isCommitting = true
        val queueSnapshot = queue.toList()
        val prevCompleted = _uiState.value.completedCount
        val prevGoodOrEasy = _uiState.value.goodOrEasyCount
        val prevSlashed = _uiState.value.slashedCount

        viewModelScope.launch {
            try {
                queue.removeFirstOrNull()
                val recorded = studyRepository.markTooEasy(current)
                pushUndo(
                    UndoEntry(
                        queueSnapshot = queueSnapshot,
                        wordId = current.word.id,
                        previousCard = recorded.previousCard,
                        logId = recorded.logId,
                        completedCount = prevCompleted,
                        goodOrEasyCount = prevGoodOrEasy,
                        slashedCount = prevSlashed,
                    ),
                )
                _uiState.value = _uiState.value.copy(
                    completedCount = prevCompleted + 1,
                    goodOrEasyCount = prevGoodOrEasy + 1,
                    slashedCount = prevSlashed + 1,
                    canUndo = undoStack.isNotEmpty(),
                )
                showNextCard()
            } finally {
                isCommitting = false
            }
        }
    }

    fun onUndoLastReview() {
        if (isCommitting) return
        val entry = undoStack.removeLastOrNull() ?: return
        isCommitting = true
        viewModelScope.launch {
            try {
                studyRepository.undoReview(
                    wordId = entry.wordId,
                    previousCard = entry.previousCard,
                    logId = entry.logId,
                )
                queue.clear()
                val restoredQueue = entry.queueSnapshot.mapIndexed { idx, item ->
                    if (idx == 0 && item.word.id == entry.wordId) {
                        item.copy(card = entry.previousCard)
                    } else {
                        item
                    }
                }
                queue.addAll(restoredQueue)
                _uiState.value = _uiState.value.copy(
                    completedCount = entry.completedCount,
                    goodOrEasyCount = entry.goodOrEasyCount,
                    slashedCount = entry.slashedCount,
                    canUndo = undoStack.isNotEmpty(),
                    feedbackMessage = "已撤回上一词，记忆状态已还原",
                )
                showNextCard()
            } finally {
                isCommitting = false
            }
        }
    }

    fun dismissFeedbackMessage() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
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
                allowNetworkAudio = settings.onlineSyncConsentGranted,
            )
        }
    }

    fun speakExample() {
        val example = _uiState.value.currentWord?.word?.exampleEn?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            val settings = preferencesRepository.getCurrentSettings()
            pronunciationPlayer.speak(
                ttsText = example,
                audioUrl = null,
                accent = settings.ttsAccent,
                speechRate = settings.ttsSpeechRate,
                allowNetworkAudio = false,
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

    private companion object {
        const val MAX_UNDO_STEPS = 20
    }
}
