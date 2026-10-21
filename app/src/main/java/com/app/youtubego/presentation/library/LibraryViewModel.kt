package com.app.youtubego.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.data.local.DownloadItem
import com.app.youtubego.data.local.DownloadStore
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.domain.model.Playlist
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.playback.PlayerHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Satu pintu untuk semua halaman koleksi pribadi (riwayat, tonton nanti, suka, playlist, unduhan). */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: UserLibraryStore,
    private val downloads: DownloadStore,
    private val holder: PlayerHolder,
) : ViewModel() {
    val saved: StateFlow<Set<String>> = library.saved
    val liked: StateFlow<Set<String>> = library.liked
    val playlists: StateFlow<List<Playlist>> = library.playlists
    val downloadItems: StateFlow<List<DownloadItem>> = downloads.items

    private fun videos(ids: Flow<Collection<String>>): StateFlow<List<VideoSummary>> =
        combine(ids, library.meta) { list, meta -> list.mapNotNull { meta[it] } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyVideos: StateFlow<List<VideoSummary>> = videos(library.history)
    val savedVideos: StateFlow<List<VideoSummary>> = videos(library.saved)
    val likedVideos: StateFlow<List<VideoSummary>> = videos(library.liked)

    fun playlistVideos(id: String): Flow<List<VideoSummary>> =
        combine(library.playlists, library.meta) { lists, meta ->
            lists.firstOrNull { it.id == id }?.videoIds.orEmpty().mapNotNull { meta[it] }
        }

    /** true = ditambahkan; false = sudah di antrean / sedang diputar. */
    fun enqueue(video: VideoSummary): Boolean { library.remember(video); return holder.enqueue(video) }
    fun toggleSaved(video: VideoSummary) = library.toggleSaved(video)
    fun toggleLiked(video: VideoSummary) = library.toggleLiked(video)
    fun removeHistory(id: String) = library.removeHistory(id)
    fun clearHistory() = library.clearHistory()

    fun togglePlaylistVideo(playlistId: String, video: VideoSummary) = library.togglePlaylistVideo(playlistId, video)
    fun createPlaylist(name: String, firstVideo: VideoSummary? = null): String = library.createPlaylist(name, firstVideo)
    fun renamePlaylist(id: String, name: String) = library.renamePlaylist(id, name)
    fun deletePlaylist(id: String) = library.deletePlaylist(id)
    fun removeFromPlaylist(playlistId: String, videoId: String) = library.removeFromPlaylist(playlistId, videoId)

    fun download(video: VideoSummary) = downloads.start(video)
    fun pauseDownload(id: String) = downloads.pause(id)
    fun removeDownload(id: String) = downloads.remove(id)
    fun isDownloaded(id: String): Boolean = downloads.isDone(id)
}
