package com.app.youtubego.presentation.home
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.domain.usecase.GetTrendingUseCase
import com.app.youtubego.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
data class HomeUiState(
    val category: HomeCategory = HomeCategory.ALL,
    val videos: UiState<List<VideoSummary>> = UiState.Loading,
)
@HiltViewModel
class HomeViewModel @Inject constructor(private val getTrending: GetTrendingUseCase) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null
    init { load(HomeCategory.ALL) }
    fun load(category: HomeCategory) {
        job?.cancel()
        _state.value = _state.value.copy(category = category, videos = UiState.Loading)
        job = getTrending(category).onEach { _state.value = _state.value.copy(videos = it) }.launchIn(viewModelScope)
    }
    fun retry() = load(_state.value.category)
}
