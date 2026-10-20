package com.app.youtubego.presentation.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionUiState(
    val title: String = "Koleksi",
    val videos: List<VideoSummary> = emptyList(),
    val loading: Boolean = true,
    val message: String = "",
    val unsupported: Boolean = false,
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: UserLibraryStore,
    private val repository: VideoRepository,
) : ViewModel() {
    private val kind = savedState.get<String>("kind") ?: "saved"
    private val _state = MutableStateFlow(CollectionUiState(title = titleFor(kind)))
    val state: StateFlow<CollectionUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init { refresh() }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = "")
            if (kind == "downloads" || kind == "playlists") {
                _state.value = CollectionUiState(
                    title = titleFor(kind), loading = false, unsupported = true,
                    message = if (kind == "downloads") "Unduhan offline belum tersedia. Video yang kamu simpan untuk nanti ada di koleksi Tonton nanti." else "Fitur playlist belum tersedia."
                )
                return@launch
            }
            val videos = when (kind) {
                "history" -> library.history.value
                "liked" -> resolveIds(library.liked.value.toList().asReversed())
                else -> resolveIds(library.saved.value.toList().asReversed())
            }
            _state.value = _state.value.copy(videos = videos, loading = false)
        }
    }

    fun remove(videoId: String) {
        when (kind) {
            "history" -> library.removeFromHistory(videoId)
            "liked" -> library.toggleLiked(videoId)
            else -> library.toggleSaved(videoId)
        }
        refresh()
    }

    private suspend fun resolveIds(ids: List<String>): List<VideoSummary> {
        val cache = library.summaries.value
        val found = mutableListOf<VideoSummary>()
        for (id in ids.take(MAX_ITEMS)) {
            val video = cache[id] ?: repository.videoDetail(id).getOrNull()?.summary?.also(library::cacheSummary)
            if (video != null) found += video
        }
        return found
    }

    private fun titleFor(key: String): String = when (key) {
        "history" -> "Riwayat tontonan"
        "liked" -> "Video disukai"
        "downloads" -> "Unduhan"
        "playlists" -> "Playlist"
        else -> "Tonton nanti"
    }

    private companion object { const val MAX_ITEMS = 40 }
}
