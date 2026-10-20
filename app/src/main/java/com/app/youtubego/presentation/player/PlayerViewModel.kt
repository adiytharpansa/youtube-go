package com.app.youtubego.presentation.player
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.domain.model.VideoDetail
import com.app.youtubego.domain.usecase.GetVideoDetailUseCase
import com.app.youtubego.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
@HiltViewModel
class PlayerViewModel @Inject constructor(savedState: SavedStateHandle, getDetail: GetVideoDetailUseCase) : ViewModel() {
    private val videoId: String = checkNotNull(savedState["videoId"])
    private val _state = MutableStateFlow<UiState<VideoDetail>>(UiState.Loading)
    val state: StateFlow<UiState<VideoDetail>> = _state.asStateFlow()
    init { getDetail(videoId).onEach { _state.value = it }.launchIn(viewModelScope) }
}
