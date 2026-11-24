package com.essvocab.app.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.essvocab.app.data.StudyRepository
import com.essvocab.app.data.local.PackSummaryRow
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.data.preferences.AppPreferencesRepository
import com.essvocab.app.domain.audio.PronunciationPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DictionaryUiState(
    val query: String = "",
    val selectedFilterPackId: String? = null,
    val activeBookPackId: String = "",
    val totalActiveWords: Int = 0,
    val totalMasteredWords: Int = 0,
    val packs: List<PackSummaryRow> = emptyList(),
    val words: List<WordWithReview> = emptyList(),
    val notebookWords: List<WordWithReview> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val pronunciationPlayer: PronunciationPlayer,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val selectedFilterPackIdFlow = MutableStateFlow<String?>(null)

    private val filteredWordsFlow = combine(selectedFilterPackIdFlow, queryFlow) { packId, query ->
        packId to query
    }.flatMapLatest { (packId, query) ->
        studyRepository.observeWords(packId, query)
    }

    private val booksOverviewFlow = combine(
        preferencesRepository.settingsFlow,
        studyRepository.observePackSummaries(),
        studyRepository.observeTotalActiveWords(),
        studyRepository.observeBookStats(null),
    ) { settings, packs, totalWords, allStats ->
        BooksOverview(
            activeBookPackId = settings.selectedPackId,
            packs = packs,
            totalWords = totalWords,
            totalMasteredWords = allStats.masteredWords,
        )
    }

    val uiState: StateFlow<DictionaryUiState> = combine(
        queryFlow,
        selectedFilterPackIdFlow,
        booksOverviewFlow,
        filteredWordsFlow,
        studyRepository.observeNotebookWords(),
    ) { query, filterPackId, overview, words, notebookWords ->
        DictionaryUiState(
            query = query,
            selectedFilterPackId = filterPackId,
            activeBookPackId = overview.activeBookPackId,
            totalActiveWords = overview.totalWords,
            totalMasteredWords = overview.totalMasteredWords,
            packs = overview.packs,
            words = words,
            notebookWords = notebookWords,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DictionaryUiState(),
    )

    fun onQueryChange(newQuery: String) {
        queryFlow.value = newQuery
    }

    fun selectFilterPack(packId: String?) {
        selectedFilterPackIdFlow.value = packId
    }

    fun selectActiveBook(packId: String?) {
        viewModelScope.launch {
            preferencesRepository.updateSelectedPackId(packId)
        }
    }

    fun toggleFavorite(wordId: String) {
        viewModelScope.launch {
            studyRepository.toggleFavorite(wordId)
        }
    }

    fun restoreFromMastered(wordId: String) {
        viewModelScope.launch {
            studyRepository.restoreFromMastered(wordId)
        }
    }

    fun speakWord(item: WordWithReview) {
        viewModelScope.launch {
            val settings = preferencesRepository.getCurrentSettings()
            pronunciationPlayer.speak(
                ttsText = item.word.ttsText,
                audioUrl = item.word.audioUrl,
                accent = settings.ttsAccent,
                speechRate = settings.ttsSpeechRate,
                allowNetworkAudio = settings.onlineSyncConsentGranted,
            )
        }
    }

    private data class BooksOverview(
        val activeBookPackId: String,
        val packs: List<PackSummaryRow>,
        val totalWords: Int,
        val totalMasteredWords: Int,
    )
}
