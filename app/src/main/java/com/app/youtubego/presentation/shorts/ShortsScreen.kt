package com.app.youtubego.presentation.shorts

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.data.newpipe.DeviceStreamSource
import com.app.youtubego.domain.model.TopicCatalog
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.domain.usecase.toFriendlyMessage
import com.app.youtubego.presentation.common.EmptyState
import com.app.youtubego.presentation.common.formatViewsId
import com.app.youtubego.presentation.theme.YgAccent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class ShortsUiState(
    val videos: List<VideoSummary> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

/**
 * Shorts: video pendek vertikal, geser atas/bawah. Sumber = pencarian `duration=short` berputar di sejumlah topik,
 * disaring ke durasi <= 3 menit; halaman tak terbatas lewat putaran kueri + nomor halaman.
 */
@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val repo: VideoRepository,
    private val deviceSource: DeviceStreamSource,
    private val library: UserLibraryStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ShortsUiState())
    val state: StateFlow<ShortsUiState> = _state.asStateFlow()
    val liked: StateFlow<Set<String>> = library.liked
    val saved: StateFlow<Set<String>> = library.saved
    val subscribed: StateFlow<Set<String>> = library.subscribed

    private val queries = listOf("shorts") + TopicCatalog.all.map { "#shorts ${it.label}" }
    private var cursor = 0
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel(); cursor = 0
        _state.value = ShortsUiState(loading = true)
        job = viewModelScope.launch { fetch(initial = true) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.videos.isEmpty()) return
        _state.update { it.copy(loadingMore = true) }
        job = viewModelScope.launch { fetch(initial = false) }
    }

    /** Ambil kueri berikutnya sampai ada video baru (maks. 4 percobaan agar tidak menembak server tanpa henti). */
    private suspend fun fetch(initial: Boolean) {
        val known = _state.value.videos.mapTo(HashSet()) { it.id }
        var fresh: List<VideoSummary> = emptyList()
        var lastError: Throwable? = null
        var tries = 0
        while (fresh.isEmpty() && tries < 4) {
            tries++
            val q = queries[cursor % queries.size]
            val page = cursor / queries.size + 1
            cursor++
            repo.search(q, page = page, shortsOnly = true).fold(
                onSuccess = { list -> fresh = list.filter { it.lengthSeconds in 1..180 && known.add(it.id) } },
                onFailure = { lastError = it },
            )
        }
        _state.update { cur ->
            if (fresh.isEmpty() && cur.videos.isEmpty()) cur.copy(loading = false, loadingMore = false, error = (lastError ?: IllegalStateException("kosong")).toFriendlyMessage())
            else cur.copy(videos = cur.videos + fresh, loading = false, loadingMore = false, error = null)
        }
    }

    /** URL stream gabungan untuk satu Short: jalur perangkat dulu, lalu API server. null = tak bisa diputar. */
    suspend fun resolveStream(id: String): String? {
        val dev = withTimeoutOrNull(10_000) { deviceSource.resolve(id).getOrNull()?.combined }.orEmpty()
        dev.firstOrNull { it.url.isNotBlank() }?.let { return it.url }
        return repo.videoDetail(id).getOrNull()?.combinedStreams?.firstOrNull { it.url.isNotBlank() }?.url
    }

    fun onWatched(v: VideoSummary) = library.addHistory(v)
    fun toggleLike(v: VideoSummary) = library.toggleLiked(v)
    fun toggleSave(v: VideoSummary) = library.toggleSaved(v)
    fun toggleSubscribe(channelId: String) = library.toggleSubscribed(channelId)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(onChannelClick: (String) -> Unit, vm: ShortsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val liked by vm.liked.collectAsState()
    val saved by vm.saved.collectAsState()
    val subscribed by vm.subscribed.collectAsState()

    when {
        state.loading -> Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = YgAccent)
        }
        state.videos.isEmpty() -> EmptyState(
            Icons.Outlined.SlowMotionVideo, "Shorts belum bisa dimuat", state.error ?: "Coba lagi sebentar.",
            actionLabel = "Coba lagi", onAction = vm::load,
        )
        else -> {
            val pager = rememberPagerState { state.videos.size }
            // Muat halaman berikutnya saat tersisa 3 video lagi.
            LaunchedEffect(pager.currentPage, state.videos.size) {
                if (pager.currentPage >= state.videos.size - 3) vm.loadMore()
            }
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize().background(Color.Black), beyondViewportPageCount = 0, key = { state.videos[it].id }) { i ->
                val v = state.videos[i]
                ShortPage(
                    video = v, active = pager.currentPage == i, vm = vm,
                    liked = v.id in liked, saved = v.id in saved, subscribed = v.authorId in subscribed,
                    onChannelClick = onChannelClick,
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ShortPage(
    video: VideoSummary, active: Boolean, vm: ShortsViewModel,
    liked: Boolean, saved: Boolean, subscribed: Boolean, onChannelClick: (String) -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Poster: tampil sampai video siap, dan satu-satunya isi untuk halaman yang tidak aktif (hemat memori & data).
        AsyncImage(video.thumbnailUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (active) ShortPlayer(video, vm)

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f)))))

        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 84.dp, bottom = 24.dp)) {
            Text(
                video.author, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = video.authorId.isNotBlank()) { onChannelClick(video.authorId) },
            )
            Text(video.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            if (video.viewCount > 0) Text("${formatViewsId(video.viewCount)} tontonan", color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
        }

        Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (video.authorId.isNotBlank()) RailButton(if (subscribed) Icons.Filled.Check else Icons.Filled.Add, if (subscribed) "Diikuti" else "Ikuti", subscribed) { vm.toggleSubscribe(video.authorId) }
            RailButton(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Suka", liked) { vm.toggleLike(video) }
            RailButton(if (saved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, if (saved) "Tersimpan" else "Simpan", saved) { vm.toggleSave(video) }
            RailButton(Icons.Filled.Share, "Bagikan", false) {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${video.title}\nhttps://youtube.com/shorts/${video.id}") }
                context.startActivity(Intent.createChooser(send, "Bagikan Short"))
            }
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (active) YgAccent else Color.White)
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Pemutar milik satu Short aktif: dibuat saat halaman aktif, dilepas saat digeser/keluar tab. Ketuk = jeda/lanjut, berulang. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ShortPlayer(video: VideoSummary, vm: ShortsViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var failed by remember(video.id) { mutableStateOf(false) }
    val url by produceState<String?>(null, video.id) {
        value = vm.resolveStream(video.id)
        if (value == null) failed = true
    }
    LaunchedEffect(video.id) { vm.onWatched(video) }

    val u = url
    if (u == null) {
        if (failed) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Short ini tidak bisa diputar. Geser ke berikutnya.", color = Color.White) }
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp)) }
        return
    }

    var ready by remember(u) { mutableStateOf(false) }
    var playing by remember(u) { mutableStateOf(true) }
    var progress by remember(u) { mutableFloatStateOf(0f) }
    val exo = remember(u) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(u)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(exo, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = true }
        }
        exo.addListener(listener)
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) exo.pause() else if (e == Lifecycle.Event.ON_RESUME) exo.play()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); exo.removeListener(listener); exo.release() }
    }
    LaunchedEffect(exo) {
        while (true) {
            val d = exo.duration
            progress = if (d > 0) (exo.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize().clickable { if (exo.isPlaying) exo.pause() else exo.play() }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.player = exo
                }
            },
        )
        if (ready && !playing && !failed) Icon(Icons.Filled.PlayArrow, contentDescription = "Lanjutkan", tint = Color.White.copy(0.9f), modifier = Modifier.align(Alignment.Center).size(72.dp))
        LinearProgressIndicator(
            progress = { progress }, color = YgAccent, trackColor = Color.White.copy(0.2f),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
        )
    }
}
