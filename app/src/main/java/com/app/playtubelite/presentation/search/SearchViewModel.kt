package com.app.playtubelite.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.playtubelite.data.local.SearchHistoryStore
import com.app.playtubelite.data.local.UserLibraryStore
import com.app.playtubelite.domain.model.ChannelResult
import com.app.playtubelite.domain.model.VideoSummary
import com.app.playtubelite.domain.repository.InterestRepository
import com.app.playtubelite.domain.repository.VideoRepository
import com.app.playtubelite.domain.usecase.toFriendlyMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchPhase { Idle, Loading, Done, Error }

data class SearchUiState(
    /** Isi kolom cari saat ini. */
    val query: String = "",
    /** true = kolom cari sedang diedit (tampilkan riwayat/saran); false = tampilkan hasil. */
    val typing: Boolean = true,
    val suggestions: List<String> = emptyList(),
    /** Kueri yang benar-benar dicari (bisa berbeda dari [query] bila pengguna sedang mengetik ulang). */
    val submittedQuery: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
    val error: String = "",
    val videos: List<VideoSummary> = emptyList(),
    val channels: List<ChannelResult> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
    val page: Int = 0,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val loadMoreFailed: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: VideoRepository,
    private val interests: InterestRepository,
    private val history: SearchHistoryStore,
    private val library: UserLibraryStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val recents: StateFlow<List<String>> = history.items
    val subscribed: StateFlow<Set<String>> = library.subscribed

    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var moreJob: Job? = null

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q, typing = true) }
        suggestJob?.cancel()
        val trimmed = q.trim()
        if (trimmed.length < 2) { _state.update { it.copy(suggestions = emptyList()) }; return }
        suggestJob = viewModelScope.launch {
            delay(300) // debounce: jangan tembak jaringan tiap ketikan
            repo.suggestions(trimmed).onSuccess { list ->
                // Buang hasil basi bila pengguna sudah mengetik hal lain / sudah menekan cari.
                _state.update { cur -> if (cur.query.trim() == trimmed && cur.typing) cur.copy(suggestions = list.take(8)) else cur }
            }
        }
    }

    fun setTyping(on: Boolean) { if (_state.value.typing != on) _state.update { it.copy(typing = on) } }

    fun clearQuery() {
        suggestJob?.cancel()
        _state.update { it.copy(query = "", suggestions = emptyList(), typing = true) }
    }

    /** Panah "isi ke kolom" pada saran/riwayat: salin teks ke kolom tanpa mencari, agar bisa diedit. */
    fun insertIntoField(text: String) = onQueryChange("$text ")

    fun removeRecent(q: String) = history.remove(q)
    fun clearRecents() = history.clear()
    fun toggleSubscribe(channelId: String) { library.toggleSubscribed(channelId) }

    fun submit(q: String = _state.value.query) {
        val query = q.trim()
        if (query.isEmpty()) return
        suggestJob?.cancel(); searchJob?.cancel(); moreJob?.cancel()
        history.add(query)
        interests.recordSearch(query)
        _state.update {
            it.copy(
                query = query, typing = false, suggestions = emptyList(), submittedQuery = query,
                phase = SearchPhase.Loading, error = "", videos = emptyList(), channels = emptyList(),
                page = 0, loadingMore = false, endReached = false, loadMoreFailed = false,
            )
        }
        searchJob = viewModelScope.launch {
            val result = repo.search(query, page = 1, shortsOnly = false)
            ensureActive()
            result.fold(
                onSuccess = { list ->
                    _state.update { it.copy(phase = SearchPhase.Done, videos = list.distinctBy { v -> v.id }, page = 1, endReached = list.isEmpty()) }
                    // Channel ikut dari sesi pencarian yang sama (sudah di-cache), jadi murah.
                    repo.searchChannels(query).onSuccess { ch ->
                        _state.update { cur -> if (cur.submittedQuery == query) cur.copy(channels = ch.take(2)) else cur }
                    }
                },
                onFailure = { e -> _state.update { it.copy(phase = SearchPhase.Error, error = e.toFriendlyMessage()) } },
            )
        }
    }

    fun retry() = submit(_state.value.submittedQuery.ifBlank { _state.value.query })

    /** Halaman berikutnya (scroll tak terbatas). Aman dipanggil berulang: diabaikan bila sedang memuat / sudah habis. */
    fun loadMore() {
        val s = _state.value
        if (s.phase != SearchPhase.Done || s.loadingMore || s.endReached || s.submittedQuery.isEmpty()) return
        val q = s.submittedQuery
        val next = s.page + 1
        _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        moreJob = viewModelScope.launch {
            val result = repo.search(q, page = next, shortsOnly = false)
            ensureActive()
            result.fold(
                onSuccess = { fresh ->
                    _state.update { cur ->
                        if (cur.submittedQuery != q) cur else {
                            val seen = cur.videos.mapTo(HashSet()) { v -> v.id }
                            cur.copy(videos = cur.videos + fresh.filter { v -> seen.add(v.id) }, page = next, loadingMore = false, endReached = fresh.isEmpty())
                        }
                    }
                },
                onFailure = { _state.update { cur -> if (cur.submittedQuery != q) cur else cur.copy(loadingMore = false, loadMoreFailed = true) } },
            )
        }
    }

    fun retryLoadMore() { _state.update { it.copy(loadMoreFailed = false) }; loadMore() }

    fun setType(t: SearchType) = _state.update { it.copy(filters = it.filters.copy(type = t)) }
    fun setDate(d: UploadDate) = _state.update { it.copy(filters = it.filters.copy(date = d)) }
    fun setDuration(d: DurationFilter) = _state.update { it.copy(filters = it.filters.copy(duration = d)) }
    fun setSort(s: SortBy) = _state.update { it.copy(filters = it.filters.copy(sort = s)) }
    fun resetFilters() = _state.update { it.copy(filters = SearchFilters()) }
}
