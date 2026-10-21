package com.app.youtubego.presentation.subs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.domain.model.ChannelInfo
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.domain.usecase.toFriendlyMessage
import com.app.youtubego.presentation.common.EmptyState
import com.app.youtubego.presentation.common.ErrorBox
import com.app.youtubego.presentation.common.UiState
import com.app.youtubego.presentation.common.VideoCardShimmer
import com.app.youtubego.presentation.library.LibraryViewModel
import com.app.youtubego.presentation.library.VideoItem
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurfaceVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubsData(val channels: List<ChannelInfo>, val videos: List<VideoSummary>)

data class SubsUiState(
    val subscribedCount: Int = 0,
    val data: UiState<SubsData> = UiState.Loading,
    val refreshing: Boolean = false,
)

/** Umpan langganan lokal: video terbaru dari semua channel yang dilanggani, urut terbaru. */
@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val repo: VideoRepository,
    private val library: UserLibraryStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SubsUiState(subscribedCount = library.subscribed.value.size))
    val state: StateFlow<SubsUiState> = _state.asStateFlow()
    private var job: Job? = null
    private val cache = HashMap<String, ChannelInfo>()

    init {
        viewModelScope.launch {
            library.subscribed.collect { ids -> _state.update { it.copy(subscribedCount = ids.size) }; load(ids, silent = false) }
        }
    }

    fun refresh() { cache.clear(); load(library.subscribed.value, silent = true) }
    fun retry() { load(library.subscribed.value, silent = false) }

    private fun load(ids: Set<String>, silent: Boolean) {
        job?.cancel()
        if (ids.isEmpty()) { _state.update { it.copy(data = UiState.Success(SubsData(emptyList(), emptyList())), refreshing = false) }; return }
        _state.update { it.copy(data = if (silent || it.data is UiState.Success) it.data else UiState.Loading, refreshing = silent) }
        job = viewModelScope.launch {
            val results: List<Result<ChannelInfo>> = coroutineScope {
                ids.map { id ->
                    async {
                        cache[id]?.let { return@async Result.success(it) }
                        repo.channel(id).map { info ->
                            val latest = info.latest.ifEmpty { repo.channelLatest(id).getOrDefault(emptyList()) }
                            info.copy(latest = latest).also { cache[id] = it }
                        }
                    }
                }.awaitAll()
            }
            val channels = results.mapNotNull { it.getOrNull() }
            // Urutan avatar mengikuti urutan langganan (terbaru dilanggani di depan).
            val ordered = ids.mapNotNull { id -> channels.firstOrNull { it.id == id } }
            if (ordered.isEmpty()) {
                val err = results.firstNotNullOfOrNull { it.exceptionOrNull() }
                _state.update { it.copy(data = UiState.Error((err ?: IllegalStateException()).toFriendlyMessage()), refreshing = false) }
            } else {
                val videos = ordered.flatMap { it.latest }.distinctBy { it.id }.sortedByDescending { it.published }
                _state.update { it.copy(data = UiState.Success(SubsData(ordered, videos)), refreshing = false) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onVideoClick: (String) -> Unit, onChannelClick: (String) -> Unit, onExplore: () -> Unit,
    vm: SubscriptionsViewModel = hiltViewModel(), libVm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    if (state.subscribedCount == 0) {
        EmptyState(
            Icons.Outlined.Subscriptions, "Belum ada langganan",
            "Langganan channel favoritmu supaya video terbarunya muncul di sini.",
            actionLabel = "Jelajahi video", onAction = onExplore,
        )
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Langganan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when (val d = state.data) {
                is UiState.Loading -> LazyColumn(Modifier.fillMaxSize()) { items(4) { VideoCardShimmer() } }
                is UiState.Error -> LazyColumn(Modifier.fillMaxSize()) { item { ErrorBox(d.message) { vm.retry() } } }
                is UiState.Success -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(d.data.channels, key = { it.id }) { c ->
                                Column(
                                    Modifier.width(68.dp).clickable { onChannelClick(c.id) }.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AsyncImage(c.avatarUrl, c.name, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(56.dp).clip(CircleShape).background(YgSurfaceVariant))
                                    Text(c.name, color = YgMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 8.dp), color = com.app.youtubego.presentation.theme.YgOutline)
                    }
                    if (d.data.videos.isEmpty()) item { Text("Belum ada video baru dari channel yang kamu ikuti.", color = YgMuted, modifier = Modifier.padding(16.dp)) }
                    items(d.data.videos, key = { it.id }, contentType = { "video" }) { v -> VideoItem(v, onVideoClick, onChannelClick, libVm) }
                }
            }
        }
    }
}
