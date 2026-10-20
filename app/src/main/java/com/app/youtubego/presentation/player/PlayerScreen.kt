package com.app.youtubego.presentation.player
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.app.youtubego.presentation.common.*
import kotlinx.coroutines.delay
/**
 * Pemutar Media3 di Compose:
 * - ExoPlayer dibuat SEKALI per videoId (remember), dilepas di DisposableEffect (lifecycle-aware).
 * - Kontrol KUSTOM Compose (bukan controller bawaan PlayerView) agar tampilannya sama dengan desain:
 *   tap tengah = play/pause, slider posisi, tombol fullscreen yang mengunci orientasi landscape.
 * - Hemat data: TrackSelector dibatasi lebar pita maksimum + pilih formatStreams gabungan
 *   berkualitas paling rendah yang layak lebih dulu (pola web: 360p gabungan).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(videoId: String, onBack: () -> Unit, onVideoClick: (String) -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFullscreen by remember { mutableStateOf(false) }
    val player = remember(videoId) {
        ExoPlayer.Builder(context).setTrackSelector(
            DefaultTrackSelector(context).apply { setParameters(buildUponParameters().setMaxVideoSize(1280, 720).setMaxAudioBitrate(128_000)) }
        ).build()
    }
    DisposableEffect(lifecycleOwner, player) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) { Lifecycle.Event.ON_PAUSE -> player.pause(); else -> Unit }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            // Pulihkan orientasi & system bars bila layar ditinggalkan saat fullscreen.
            context.findActivity()?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            player.release()
        }
    }
    var playerError by remember(videoId) { mutableStateOf<String?>(null) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "Aliran video gagal diambil dari server (galat HTTP). Server video mungkin sedang bermasalah — coba lagi sebentar."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Koneksi ke server video terputus atau terlalu lambat. Coba lagi."
                    else -> "Video gagal diputar (${error.errorCodeName}). Coba lagi."
                }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    BackHandler(enabled = isFullscreen) { isFullscreen = false }
    // Terapkan fullscreen: kunci orientasi di Activity pemilik.
    LaunchedEffect(isFullscreen) {
        val act = context.findActivity() ?: return@LaunchedEffect
        act.requestedOrientation = if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val ctrl = WindowCompat.getInsetsController(act.window, act.window.decorView)
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isFullscreen) ctrl.hide(WindowInsetsCompat.Type.systemBars()) else ctrl.show(WindowInsetsCompat.Type.systemBars())
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().then(if (isFullscreen) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
                modifier = Modifier.fillMaxSize(),
            )
            PlayerControls(player = player, isFullscreen = isFullscreen, onToggleFullscreen = { isFullscreen = !isFullscreen }, onBack = onBack)
            playerError?.let { msg ->
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(0.7f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(msg, color = Color.White)
                    TextButton(onClick = { playerError = null; vm.retry() }) { Text("Coba lagi") }
                }
            }
        }
        if (!isFullscreen) {
            when (val s = state) {
                is UiState.Loading -> Column { VideoCardShimmer() }
                is UiState.Error -> ErrorBox(s.message) { vm.retry() }
                is UiState.Success -> {
                    val d = s.data
                    // Siapkan media SEKALI per set URL segar: utamakan stream gabungan
                    // terendah (hemat data), cadangan DASH adaptif (manifest lewat proksi,
                    // segmennya dialirkan jalur videoplayback), lalu HLS. Bila ketiganya
                    // kosong dari server, tampilkan sebab sebenarnya — jangan diam.
                    LaunchedEffect(d.combinedStreams.firstOrNull()?.url, d.dashUrl, d.hlsUrl) {
                        val combined = d.combinedStreams.firstOrNull()?.url
                        val item = when {
                            !combined.isNullOrBlank() -> MediaItem.fromUri(combined)
                            !d.dashUrl.isNullOrBlank() -> MediaItem.Builder().setUri(d.dashUrl).setMimeType(MimeTypes.APPLICATION_MPD).build()
                            !d.hlsUrl.isNullOrBlank() -> MediaItem.Builder().setUri(d.hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build()
                            else -> null
                        }
                        if (item != null) { player.setMediaItem(item); player.prepare(); player.playWhenReady = true }
                        else playerError = "Server tidak menyediakan format putar untuk video ini — format gabungan, DASH, dan HLS semuanya kosong. Coba lagi nanti atau pilih video lain."
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Column(Modifier.padding(14.dp)) {
                                Text(d.summary.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text("${formatViews(d.summary.viewCount)} x ditonton", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                if (d.description.isNotBlank()) Text(d.description, maxLines = 4, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        items(d.recommendations, key = { it.id }) { v -> VideoCard(v, onVideoClick) }
                    }
                }
            }
        }
    }
}
@Composable
private fun PlayerControls(player: ExoPlayer, isFullscreen: Boolean, onToggleFullscreen: () -> Unit, onBack: () -> Unit) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) { playing = player.isPlaying; position = player.currentPosition.coerceAtLeast(0); duration = player.duration.coerceAtLeast(0); delay(500) }
    }
    Box(Modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White) }
        IconButton(
            onClick = { if (player.isPlaying) player.pause() else player.play() },
            modifier = Modifier.align(Alignment.Center).size(64.dp).background(Color.Black.copy(0.45f), androidx.compose.foundation.shape.CircleShape),
        ) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(position / 1000), color = Color.White, style = MaterialTheme.typography.labelSmall)
            Slider(
                value = if (dragging) dragFrac else if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { dragging = true; dragFrac = it },
                onValueChangeFinished = { if (duration > 0) player.seekTo((dragFrac * duration).toLong()); dragging = false },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(formatDuration(duration / 1000), color = Color.White, style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = onToggleFullscreen) { Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Fullscreen", tint = Color.White) }
        }
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var c = this
    while (c is android.content.ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}
