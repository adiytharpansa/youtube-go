package com.app.youtubego.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.domain.usecase.toFriendlyMessage
import com.app.youtubego.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShortsViewModel @Inject constructor(private val repository: VideoRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<VideoSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<VideoSummary>>> = _state.asStateFlow()
    private var request = 0

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
}
