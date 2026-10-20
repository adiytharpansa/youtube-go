package com.app.youtubego.presentation.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.data.local.PlaylistStore
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistHomeViewModel @Inject constructor(private val playlists: PlaylistStore) : ViewModel() {
    val items = playlists.playlists
    fun create(name: String) = playlists.create(name)
    fun delete(id: String) = playlists.delete(id)
}

data class PlaylistDetailUiState(
    val name: String = "Playlist",
    val videos: List<VideoSummary> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val playlists: PlaylistStore,
    private val library: UserLibraryStore,
    private val repository: VideoRepository,
) : ViewModel() {
    private val playlistId = savedState.get<String>("playlistId").orEmpty()
    private val _state = MutableStateFlow(PlaylistDetailUiState())
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val playlist = playlists.playlists.value.firstOrNull { it.id == playlistId }
            if (playlist == null) {
                _state.value = PlaylistDetailUiState(name = "Playlist tidak ditemukan", loading = false)
                return@launch
            }
            _state.value = PlaylistDetailUiState(name = playlist.name, loading = true)
            val videos = playlist.videoIds.mapNotNull { id ->
                library.summaries.value[id] ?: repository.videoDetail(id).getOrNull()?.summary?.also(library::cacheSummary)
            }
            _state.value = PlaylistDetailUiState(name = playlist.name, videos = videos, loading = false)
        }
    }

    fun remove(videoId: String) {
        playlists.removeVideo(playlistId, videoId)
        refresh()
    }
}
