package com.app.youtubego.presentation.subs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.presentation.common.shimmer
import com.app.youtubego.presentation.navigation.LocalTabReselect
import com.app.youtubego.presentation.navigation.Routes
import com.app.youtubego.presentation.theme.YgAccent
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
import com.app.youtubego.presentation.common.ScreenHeader
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

/** Channel saran untuk keadaan kosong: diambil dari video trending asli (tanpa data contoh). */
data class SuggestedChannel(val id: String, val name: String, val avatarUrl: String)

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

    private val _suggestions = MutableStateFlow<UiState<List<SuggestedChannel>>>(UiState.Loading)
    val suggestions: StateFlow<UiState<List<SuggestedChannel>>> = _suggestions.asStateFlow()
    private var suggestionJob: Job? = null

    /** Memuat saran channel sekali (atau ulang bila sebelumnya gagal). Aman dipanggil berulang. */
    fun loadSuggestions() {
        if (_suggestions.value is UiState.Success || suggestionJob?.isActive == true) return
        suggestionJob = viewModelScope.launch {
            _suggestions.value = UiState.Loading
            repo.trending(HomeCategory.ALL)
                .onSuccess { videos ->
                    val list = videos.filter { it.authorId.isNotBlank() && it.author.isNotBlank() }
                        .distinctBy { it.authorId }.take(10)
                        .map { SuggestedChannel(it.authorId, it.author, it.authorAvatarUrl) }
                    _suggestions.value = UiState.Success(list)
                }
                .onFailure { _suggestions.value = UiState.Error(it.toFriendlyMessage()) }
        }
    }

    fun subscribe(channelId: String) { if (channelId !in library.subscribed.value) library.toggleSubscribed(channelId) }

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
    val listState = rememberLazyListState()
    // Ketuk ulang tab "Langganan" saat sudah aktif: gulir ke paling atas.
    val reselect = LocalTabReselect.current
    LaunchedEffect(reselect) { reselect.collect { if (it == Routes.SUBS) listState.animateScrollToItem(0) } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header selalu ada (kosong maupun berisi) supaya layar tidak melompat setelah berlangganan.
        ScreenHeader("Langganan")
        if (state.subscribedCount == 0) {
            val suggestions by vm.suggestions.collectAsState()
            LaunchedEffect(Unit) { vm.loadSuggestions() }
            EmptyState(
                Icons.Outlined.Subscriptions, "Belum ada langganan",
                "Langganan channel favoritmu supaya video terbarunya muncul di sini.",
                actionLabel = "Jelajahi video", onAction = onExplore,
                extra = { SuggestedChannels(suggestions, onChannelClick, vm::subscribe, vm::loadSuggestions) },
            )
            return@Column
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when (val d = state.data) {
                is UiState.Loading -> LazyColumn(Modifier.fillMaxSize()) { items(4) { VideoCardShimmer() } }
                is UiState.Error -> LazyColumn(Modifier.fillMaxSize()) { item { ErrorBox(d.message) { vm.retry() } } }
                is UiState.Success -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
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

/** Baris "Channel populer" di keadaan kosong: shimmer saat memuat, hilang diam-diam bila gagal (ada tombol coba lagi kecil). */
@Composable
private fun ColumnScope.SuggestedChannels(
    state: UiState<List<SuggestedChannel>>,
    onOpen: (String) -> Unit, onSubscribe: (String) -> Unit, onRetry: () -> Unit,
) {
    Spacer(Modifier.height(32.dp))
    Text("Channel populer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, bottom = 12.dp))
    when (state) {
        is UiState.Loading -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
            items(5) {
                Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).shimmer())
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(72.dp).height(12.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(84.dp).height(32.dp).clip(CircleShape).shimmer())
                }
            }
        }
        is UiState.Error -> TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Gagal memuat saran. Coba lagi", color = YgMuted)
        }
        is UiState.Success -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.data, key = { it.id }) { c -> SuggestedChannelCard(c, onOpen, onSubscribe) }
        }
    }
}

@Composable
private fun SuggestedChannelCard(c: SuggestedChannel, onOpen: (String) -> Unit, onSubscribe: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.width(104.dp).clip(MaterialTheme.shapes.medium).clickable { onOpen(c.id) }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (c.avatarUrl.isNotBlank()) {
            AsyncImage(c.avatarUrl, c.name, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(CircleShape).background(YgSurfaceVariant))
        } else {
            Box(Modifier.size(64.dp).clip(CircleShape).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
                Text(c.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = YgMuted)
            }
        }
        Text(
            c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        // Pil aksen kecil: tinggi 32dp tampak ringkas, area sentuh diperluas lewat padding vertikal pembungkus.
        Box(
            Modifier.heightIn(min = 32.dp).clip(CircleShape).background(YgAccent)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSubscribe(c.id)
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Langganan", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1) }
    }
}
