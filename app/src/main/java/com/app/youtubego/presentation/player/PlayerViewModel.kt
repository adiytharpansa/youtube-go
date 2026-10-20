package com.app.youtubego.presentation.player
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.data.local.OfflineDownloadsRepository
import com.app.youtubego.data.local.PlaylistStore
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.data.newpipe.DevicePlayback
import com.app.youtubego.data.newpipe.DeviceStreamSource
import com.app.youtubego.domain.model.CommentItem
import com.app.youtubego.domain.model.VideoDetail
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.InterestRepository
import com.app.youtubego.domain.usecase.GetCommentsUseCase
import com.app.youtubego.domain.usecase.GetVideoDetailUseCase
import com.app.youtubego.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val getDetail: GetVideoDetailUseCase,
    private val getComments: GetCommentsUseCase,
    private val deviceSource: DeviceStreamSource,
    private val interests: InterestRepository,
    private val library: UserLibraryStore,
    private val downloads: OfflineDownloadsRepository,
    private val playlistStore: PlaylistStore,
) : ViewModel() {
    private val videoId: String = checkNotNull(savedState["videoId"])
    private val _state = MutableStateFlow<UiState<VideoDetail>>(UiState.Loading)
    val state: StateFlow<UiState<VideoDetail>> = _state.asStateFlow()
    /** Stream hasil ekstraksi langsung di perangkat (jalur putar utama). null = belum selesai atau gagal. */
    private val _device = MutableStateFlow<DevicePlayback?>(null)
    val device: StateFlow<DevicePlayback?> = _device.asStateFlow()
    /** true sesudah percobaan ekstraksi di perangkat selesai, apa pun hasilnya. */
    private val _deviceSettled = MutableStateFlow(false)
    val deviceSettled: StateFlow<Boolean> = _deviceSettled.asStateFlow()
    private val _comments = MutableStateFlow<UiState<List<CommentItem>>>(UiState.Loading)
    val comments: StateFlow<UiState<List<CommentItem>>> = _comments.asStateFlow()
    val liked: StateFlow<Set<String>> = library.liked
    val saved: StateFlow<Set<String>> = library.saved
    val subscribed: StateFlow<Set<String>> = library.subscribed
    val autoplay: StateFlow<Boolean> = library.autoplay
    val playlists = playlistStore.playlists
    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage.asStateFlow()
    private var job: Job? = null
    private var deviceJob: Job? = null
    private var commentsJob: Job? = null
    private var watchRecorded = false
    /** Catat sinyal minat sekali per video: judul -> topik, channel -> afinitas. */
    private fun recordWatchOnce(v: VideoSummary) {
        if (!watchRecorded) {
            watchRecorded = true
            interests.recordWatch(v)
            library.recordWatch(v)
        }
    }
    init { load() }
    /** Muat ulang dari awal (URL putar kedaluwarsa, jadi retry WAJIB mengambil data segar dari kedua jalur, bukan memutar ulang URL lama). */
    fun retry() { load() }
    fun toggleLike() = library.toggleLiked(videoId)
    fun toggleSave() = library.toggleSaved(videoId)
    fun toggleSubscribe(channelId: String) = library.toggleSubscribed(channelId)
    fun setAutoplay(on: Boolean) = library.setAutoplay(on)
    fun downloadCurrent() {
        val video = (_state.value as? UiState.Success)?.data?.summary
        if (video == null) {
            _downloadMessage.value = "Tunggu sampai informasi video selesai dimuat."
            return
        }
        viewModelScope.launch {
            _downloadMessage.value = "Menyiapkan unduhan…"
            val result = downloads.enqueue(video)
            _downloadMessage.value = result.fold(
                onSuccess = { if (it) "Unduhan dimulai." else "Video ini sudah ada dalam daftar unduhan." },
                onFailure = { it.message?.takeIf { message -> message.isNotBlank() } ?: "Unduhan gagal dimulai." }
            )
        }
    }
    fun dismissDownloadMessage() { _downloadMessage.value = null }
    fun addToPlaylist(playlistId: String) {
        playlistStore.addVideo(playlistId, videoId)
        _downloadMessage.value = "Video ditambahkan ke playlist."
    }
    fun createPlaylistWithCurrentVideo(name: String): String? {
        val id = playlistStore.create(name) ?: return null
        playlistStore.addVideo(id, videoId)
        _downloadMessage.value = "Playlist dibuat dan video ditambahkan."
        return id
    }
    private fun load() {
        job?.cancel()
        deviceJob?.cancel()
        commentsJob?.cancel()
        _device.value = null
        _deviceSettled.value = false
        commentsJob = getComments(videoId).onEach { _comments.value = it }.launchIn(viewModelScope)
        job = getDetail(videoId).onEach { result ->
            if (result is UiState.Success) recordWatchOnce(result.data.summary)
            val dev = _device.value
            _state.value = if (result is UiState.Error && dev != null) UiState.Success(fallbackDetail(dev)) else result
        }.launchIn(viewModelScope)
        deviceJob = viewModelScope.launch {
            val resolved = deviceSource.resolve(videoId).getOrNull()
            _device.value = resolved
            resolved?.let { recordWatchOnce(it.summary) }
            _deviceSettled.value = true
            // Detail API terlanjur galat duluan, tetapi perangkat membawa data: tampilkan detail cadangan.
            if (resolved != null && _state.value is UiState.Error) {
                _state.value = UiState.Success(fallbackDetail(resolved))
            }
        }
    }
    /** Detail cadangan dari metadata perangkat (tanpa rekomendasi/takarir) bila API utama tidak terjangkau. */
    private fun fallbackDetail(d: DevicePlayback): VideoDetail = VideoDetail(
        summary = d.summary,
        description = d.description,
        likeCount = 0L,
        subscriberText = "",
        combinedStreams = emptyList(),
        dashUrl = null,
        hlsUrl = null,
        captions = emptyList(),
        recommendations = emptyList(),
    )
}
