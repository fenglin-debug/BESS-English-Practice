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
    val selectedPackId: String? = null,
    val packs: List<PackSummaryRow> = emptyList(),
    val words: List<WordWithReview> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val preferencesRepository: AppPreferencesRepository,
    private val pronunciationPlayer: PronunciationPlayer,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val selectedPackIdFlow = MutableStateFlow<String?>(null)

    private val filteredWordsFlow = combine(selectedPackIdFlow, queryFlow) { packId, query ->
        packId to query
    }.flatMapLatest { (packId, query) ->
        studyRepository.observeWords(packId, query)
    }

    val uiState: StateFlow<DictionaryUiState> = combine(
        queryFlow,
        selectedPackIdFlow,
        studyRepository.observePackSummaries(),
        filteredWordsFlow,
    ) { query, selectedPackId, packs, words ->
        DictionaryUiState(
            query = query,
            selectedPackId = selectedPackId,
            packs = packs,
            words = words,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DictionaryUiState(),
    )

    fun onQueryChange(newQuery: String) {
        queryFlow.value = newQuery
    }

    fun selectPack(packId: String?) {
        selectedPackIdFlow.value = packId
    }

    fun speakWord(item: WordWithReview) {
        viewModelScope.launch {
            val settings = preferencesRepository.getCurrentSettings()
            pronunciationPlayer.speak(
                ttsText = item.word.ttsText,
                audioUrl = item.word.audioUrl,
                accent = settings.ttsAccent,
                speechRate = settings.ttsSpeechRate,
            )
        }
    }
}
