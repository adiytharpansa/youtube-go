package com.app.youtubego.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.data.newpipe.DevicePlayback
import com.app.youtubego.data.newpipe.DeviceStreamSource
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.domain.usecase.toFriendlyMessage
import com.app.youtubego.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShortsPlaybackState(
    val videoId: String? = null,
    val loading: Boolean = false,
    val url: String? = null,
    val mimeType: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val deviceSource: DeviceStreamSource,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<VideoSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<VideoSummary>>> = _state.asStateFlow()
    private val _playback = MutableStateFlow(ShortsPlaybackState())
    val playback: StateFlow<ShortsPlaybackState> = _playback.asStateFlow()
    private var request = 0
    private var playbackJob: Job? = null

    init { refresh() }

    fun refresh() {
        val token = ++request
        viewModelScope.launch {
            _state.value = UiState.Loading
            val result = repository.search("shorts indonesia", shortsOnly = true)
            if (token != request) return@launch
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.toFriendlyMessage()) }
            )
        }
    }

    fun preparePlayback(videoId: String) {
        if (playback.value.videoId == videoId && (playback.value.loading || playback.value.url != null)) return
        playbackJob?.cancel()
        _playback.value = ShortsPlaybackState(videoId = videoId, loading = true)
        playbackJob = viewModelScope.launch {
            val result = deviceSource.resolve(videoId)
            _playback.value = result.fold(
                onSuccess = { device ->
                    val progressive = device.combined.firstOrNull()
                    when {
                        progressive != null && progressive.url.isNotBlank() -> ShortsPlaybackState(videoId, url = progressive.url, mimeType = progressive.mimeType)
                        !device.hlsUrl.isNullOrBlank() -> ShortsPlaybackState(videoId, url = device.hlsUrl, mimeType = "application/x-mpegURL")
                        !device.dashUrl.isNullOrBlank() -> ShortsPlaybackState(videoId, url = device.dashUrl, mimeType = "application/dash+xml")
                        else -> ShortsPlaybackState(videoId, error = "Stream video pendek tidak tersedia.")
                    }
                },
                onFailure = { ShortsPlaybackState(videoId, error = it.toFriendlyMessage()) }
            )
        }
    }
}
