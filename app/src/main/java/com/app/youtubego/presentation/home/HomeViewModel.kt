package com.app.youtubego.presentation.home
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.domain.repository.InterestRepository
import com.app.youtubego.domain.usecase.GetForYouFeedUseCase
import com.app.youtubego.domain.usecase.GetTrendingUseCase
import com.app.youtubego.domain.usecase.LoadMoreHomeUseCase
import com.app.youtubego.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
data class HomeUiState(
    val category: HomeCategory = HomeCategory.FOR_YOU,
    val videos: UiState<List<VideoSummary>> = UiState.Loading,
    val refreshing: Boolean = false,
    val showInterestSheet: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val loadMoreFailed: Boolean = false,
)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getTrending: GetTrendingUseCase,
    private val getForYou: GetForYouFeedUseCase,
    private val loadMoreHome: LoadMoreHomeUseCase,
    private val interestRepo: InterestRepository,
    private val deviceSource: com.app.youtubego.data.newpipe.DeviceStreamSource,
    private val holder: com.app.youtubego.playback.PlayerHolder,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(showInterestSheet = !interestRepo.profile.value.onboarded))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    val profile = interestRepo.profile
    private var job: kotlinx.coroutines.Job? = null
    private var moreJob: kotlinx.coroutines.Job? = null
    private var page = 1
    /** Revisi profil minat saat feed terakhir dimuat; beda dengan sekarang = feed sudah basi. */
    private var loadedRev = -1L
    init { load(HomeCategory.FOR_YOU) }

    /** silent = pertahankan daftar lama selama memuat (tarik-segarkan / kembali ke beranda). */
    fun load(category: HomeCategory, silent: Boolean = false) {
        job?.cancel()
        moreJob?.cancel()
        page = 1
        loadedRev = interestRepo.profile.value.rev
        _state.update {
            it.copy(category = category, videos = if (silent) it.videos else UiState.Loading, refreshing = silent,
                loadingMore = false, endReached = false, loadMoreFailed = false)
        }
        val flow = if (category == HomeCategory.FOR_YOU) getForYou() else getTrending(category)
        job = flow.onEach { r ->
            when {
                r is UiState.Loading -> if (!silent) _state.update { it.copy(videos = r) }
                silent && r is UiState.Error && _state.value.videos is UiState.Success -> _state.update { it.copy(refreshing = false) }
                else -> _state.update { it.copy(videos = r, refreshing = false) }
            }
            if (r is UiState.Success) prefetchTop(r.data)
        }.launchIn(viewModelScope)
    }
    /** Ekstrak 2 video teratas di Beranda lebih dulu (Wi-Fi saja): ketukan pertama langsung memutar. */
    private fun prefetchTop(list: List<VideoSummary>) {
        if (holder.isMetered()) return
        list.asSequence().filter { it.id.isNotBlank() && !it.isLive }.take(2).forEach { deviceSource.prefetch(it.id) }
    }
    /**
     * Scroll tak terbatas: ambil halaman berikutnya dan tambahkan ke daftar. Halaman yang isinya duplikat semua
     * dilewati otomatis (maks. 3 kali); halaman kosong = habis; galat = tombol "Coba lagi" di dasar daftar.
     */
    fun loadMore() {
        val s = _state.value
        val current = (s.videos as? UiState.Success)?.data ?: return
        if (s.loadingMore || s.endReached || s.refreshing || current.isEmpty()) return
        val category = s.category
        _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        moreJob = viewModelScope.launch {
            val known = current.mapTo(HashSet()) { it.id }
            var fresh: List<VideoSummary> = emptyList()
            var ended = false
            var failed = false
            var tries = 0
            while (fresh.isEmpty() && !ended && !failed && tries < 3) {
                tries++
                val next = page + 1
                loadMoreHome(category, next).fold(
                    onSuccess = { items ->
                        page = next
                        if (items.isEmpty()) ended = true else fresh = items.filter { it.id.isNotBlank() && known.add(it.id) }
                    },
                    onFailure = { failed = true },
                )
            }
            _state.update { cur ->
                val list = (cur.videos as? UiState.Success)?.data
                if (cur.category != category || list == null) cur
                else cur.copy(
                    videos = UiState.Success(list + fresh), loadingMore = false,
                    endReached = ended, loadMoreFailed = failed,
                )
            }
        }
    }

    fun retry() = load(_state.value.category)
    fun refresh() = load(_state.value.category, silent = true)

    /** Dipanggil saat beranda kembali tampil / panel minat ditutup: segarkan bila minat berubah. */
    fun refreshIfStale() {
        val s = _state.value
        if (s.category == HomeCategory.FOR_YOU && s.videos !is UiState.Loading && interestRepo.profile.value.rev != loadedRev) load(HomeCategory.FOR_YOU, silent = true)
    }

    fun openInterests() = _state.update { it.copy(showInterestSheet = true) }
    fun closeInterests() {
        interestRepo.markOnboarded()
        _state.update { it.copy(showInterestSheet = false) }
        refreshIfStale()
    }
    fun toggleSeed(id: String) {
        val cur = interestRepo.profile.value.seeds
        interestRepo.setSeeds(if (id in cur) cur - id else cur + id)
    }
    fun forgetTopic(topic: String) = interestRepo.forgetTopic(topic)
    fun forgetChannel(id: String) = interestRepo.forgetChannel(id)
    fun resetInterests() = interestRepo.reset()
}
