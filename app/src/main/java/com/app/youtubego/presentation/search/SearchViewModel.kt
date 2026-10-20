package com.app.youtubego.presentation.search
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.domain.usecase.SearchVideosUseCase
import com.app.youtubego.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
data class SearchUiState(val query: String = "", val suggestions: List<String> = emptyList(), val results: UiState<List<VideoSummary>> = UiState.Success(emptyList()), val shortsOnly: Boolean = false)
@HiltViewModel
class SearchViewModel @Inject constructor(private val searchVideos: SearchVideosUseCase, private val repo: VideoRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        suggestJob?.cancel()
        if (q.trim().length < 2) { _state.value = _state.value.copy(suggestions = emptyList()); return }
        suggestJob = viewModelScope.launch {
            delay(350) // debounce persis pola web: jangan tembak API tiap ketikan
            repo.suggestions(q.trim()).onSuccess { _state.value = _state.value.copy(suggestions = it.take(8)) }
        }
    }
    fun submit(q: String = _state.value.query) {
        val query = q.trim(); if (query.isEmpty()) return
        _state.value = _state.value.copy(query = query, suggestions = emptyList())
        searchJob?.cancel()
        searchJob = searchVideos(query, _state.value.shortsOnly).onEach { _state.value = _state.value.copy(results = it) }.launchIn(viewModelScope)
    }
    fun setShortsOnly(on: Boolean) { _state.value = _state.value.copy(shortsOnly = on); if (_state.value.query.isNotBlank()) submit() }
}
