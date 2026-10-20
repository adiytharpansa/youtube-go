package com.app.youtubego.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.common.EmptyState
import com.app.youtubego.presentation.common.UiState
import com.app.youtubego.presentation.common.formatDuration
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgBackground

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    onVideoClick: (String) -> Unit,
    vm: ShortsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val playback by vm.playback.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    LaunchedEffect(state) { if (state !is UiState.Success) player.pause() }
    var isPlaying by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Shorts", fontWeight = FontWeight.ExtraBold)
                        Text("BETA", color = YgAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                },
                actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Muat ulang") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YgBackground)
            )
        }
    ) { padding ->
        when (val content = state) {
            UiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = YgAccent)
            }
            is UiState.Error -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.PlayArrow,
                    title = "Shorts gagal dimuat",
                    message = content.message,
                    actionLabel = "Coba lagi",
                    onAction = vm::refresh
                )
            }
            is UiState.Success -> if (content.data.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.PlayArrow,
                    title = "Belum ada Shorts",
                    message = "Video pendek belum tersedia. Coba muat ulang sebentar lagi.",
                    actionLabel = "Muat ulang",
                    onAction = vm::refresh,
                    modifier = Modifier.padding(padding)
                )
            } else {
                ShortsPager(
                    videos = content.data,
                    playback = playback,
                    player = player,
                    isPlaying = isPlaying,
                    onPrepare = vm::preparePlayback,
                    onVideoClick = onVideoClick,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortsPager(
    videos: List<VideoSummary>,
    playback: ShortsPlaybackState,
    player: ExoPlayer,
    isPlaying: Boolean,
    onPrepare: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(pageCount = { videos.size })
    val current = videos.getOrNull(pager.currentPage)
    LaunchedEffect(current?.id) {
        player.pause()
        player.clearMediaItems()
        current?.let(onPrepare)
    }
    LaunchedEffect(current?.id, playback.videoId, playback.url) {
        val url = playback.url
        if (current != null && playback.videoId == current.id && !url.isNullOrBlank()) {
            val media = MediaItem.Builder().setUri(url).apply {
                playback.mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
            }.build()
            player.setMediaItem(media)
            player.prepare()
            player.playWhenReady = true
        } else {
            player.pause()
        }
    }

    VerticalPager(state = pager, modifier = modifier.fillMaxSize()) { page ->
        val video = videos[page]
        val active = page == pager.currentPage
        val hasStream = active && playback.videoId == video.id && !playback.url.isNullOrBlank()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (hasStream) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            keepScreenOn = true
                        }
                    },
                    update = { view -> view.player = if (active) player else null },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(0.05f), Color.Transparent, Color.Black.copy(0.86f)))
                )
            )
            if (active && !hasStream) {
                Box(Modifier.align(Alignment.Center).size(68.dp).clip(CircleShape).background(Color.Black.copy(0.58f)), contentAlignment = Alignment.Center) {
                    if (playback.loading && playback.videoId == video.id) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    } else {
                        IconButton(onClick = { onVideoClick(video.id) }) {
                            Icon(Icons.Default.PlayArrow, "Buka pemutar", tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                    }
                }
            } else if (active && hasStream && !isPlaying) {
                Box(Modifier.align(Alignment.Center).size(68.dp).clip(CircleShape).background(Color.Black.copy(0.58f)), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { player.play() }) {
                        Icon(Icons.Default.PlayArrow, "Putar", tint = Color.White, modifier = Modifier.size(42.dp))
                    }
                }
            } else if (active && hasStream && isPlaying) {
                IconButton(onClick = { player.pause() }, modifier = Modifier.align(Alignment.Center).size(64.dp)) {
                    Icon(Icons.Default.Pause, "Jeda", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(42.dp))
                }
            }
            ShortsInfo(video = video, index = page, total = videos.size, modifier = Modifier.align(Alignment.BottomStart))
            if (active && playback.error != null && playback.videoId == video.id) {
                Text(playback.error.orEmpty(), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))
            }
        }
    }
}

@Composable
private fun ShortsInfo(video: VideoSummary, index: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, bottom = 24.dp)) {
        Text("${index + 1} / $total  ·  VIDEO PENDEK", color = Color.White.copy(0.78f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(9.dp))
        Text(video.title, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Text("${video.author}  ·  ${video.viewCount} ditonton", color = Color.White.copy(0.78f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        Text("Geser ke atas untuk video berikutnya", color = Color.White.copy(0.72f), style = MaterialTheme.typography.labelSmall)
    }
}
