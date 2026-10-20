package com.app.youtubego.presentation.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.domain.model.ChannelInfo
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.presentation.common.UiState
import com.app.youtubego.domain.usecase.toFriendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: VideoRepository,
    private val library: UserLibraryStore,
) : ViewModel() {
    private val channelId = savedState.get<String>("ucid").orEmpty()
    private val _state = MutableStateFlow<UiState<ChannelInfo>>(UiState.Loading)
    val state: StateFlow<UiState<ChannelInfo>> = _state.asStateFlow()
    val subscribed = library.subscribed

    init { refresh() }

    fun toggleSubscribe() = library.toggleSubscribed(channelId)

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val result = repository.channel(channelId)
            _state.value = result.fold(
                onSuccess = { info ->
                    val latest = if (info.latest.isNotEmpty()) info.latest else repository.channelLatest(channelId).getOrDefault(emptyList())
                    UiState.Success(info.copy(latest = latest))
                },
                onFailure = { UiState.Error(it.toFriendlyMessage()) }
            )
        }
    }
}
