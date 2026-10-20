package com.app.youtubego.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionsUiState(
    val videos: UiState<List<VideoSummary>> = UiState.Loading,
    val channelCount: Int = 0,
    val refreshing: Boolean = false,
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val library: UserLibraryStore,
    private val repository: VideoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SubscriptionsUiState())
    val state: StateFlow<SubscriptionsUiState> = _state.asStateFlow()
    private var feedJob: Job? = null

    init {
        viewModelScope.launch {
            library.subscribed.collectLatest { ids -> load(ids, silent = false) }
        }
    }

    fun refresh() = load(library.subscribed.value, silent = true)

    private fun load(ids: Set<String>, silent: Boolean) {
        feedJob?.cancel()
        if (ids.isEmpty()) {
            _state.value = SubscriptionsUiState(videos = UiState.Success(emptyList()))
            return
        }
        _state.value = _state.value.copy(
            videos = if (silent && _state.value.videos is UiState.Success) _state.value.videos else UiState.Loading,
            channelCount = ids.size,
            refreshing = silent
        )
        feedJob = viewModelScope.launch {
            val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
            val results = coroutineScope {
                ids.take(MAX_CHANNELS).map { id ->
                    async { semaphore.withPermit { repository.channelLatest(id) } }
                }.awaitAll()
            }
            val videos = results.mapNotNull { it.getOrNull() }
                .flatten()
                .distinctBy { it.id }
                .sortedByDescending { it.published }
            val failed = results.all { it.isFailure }
            _state.value = when {
                failed -> _state.value.copy(videos = UiState.Error("Feed langganan gagal dimuat. Periksa koneksi lalu coba lagi."), refreshing = false)
                else -> _state.value.copy(videos = UiState.Success(videos), refreshing = false)
            }
        }
    }

    private companion object {
        const val MAX_CHANNELS = 25
        const val MAX_PARALLEL_REQUESTS = 5
    }
}
