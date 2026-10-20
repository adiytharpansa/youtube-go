package com.app.youtubego.presentation.player
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.data.newpipe.DevicePlayback
import com.app.youtubego.data.newpipe.DeviceStreamSource
import com.app.youtubego.domain.model.VideoDetail
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.InterestRepository
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
    private val deviceSource: DeviceStreamSource,
    private val interests: InterestRepository,
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
    private var job: Job? = null
    private var watchRecorded = false
    /** Catat sinyal minat sekali per video: judul -> topik, channel -> afinitas. */
    private fun recordWatchOnce(v: VideoSummary) { if (!watchRecorded) { watchRecorded = true; interests.recordWatch(v) } }
    private var deviceJob: Job? = null
    init { load() }
    /** Muat ulang dari awal (URL putar kedaluwarsa, jadi retry WAJIB mengambil data segar dari kedua jalur, bukan memutar ulang URL lama). */
    fun retry() { load() }
    private fun load() {
        job?.cancel()
        deviceJob?.cancel()
        _device.value = null
        _deviceSettled.value = false
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
