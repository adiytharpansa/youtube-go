package com.app.youtubego.presentation.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.playback.BackgroundPlaybackEffect
import com.app.youtubego.playback.PlayerHolder
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(val holder: PlayerHolder, library: UserLibraryStore) : ViewModel() {
    val backgroundPlay = library.backgroundPlay
}

/**
 * Selalu aktif di tingkat navigasi: menautkan pemutar bersama ke siklus hidup Activity (latar belakang)
 * dan menjeda pemutar utama saat layar lain yang punya pemutar sendiri (Shorts/offline) tampil.
 */
@Composable
fun PlaybackHost(pauseMain: Boolean, onOpenVideo: (String) -> Unit, vm: MiniPlayerViewModel = hiltViewModel()) {
    val backgroundPlay by vm.backgroundPlay.collectAsState()
    BackgroundPlaybackEffect(vm.holder, backgroundPlay)
    val open by rememberUpdatedState(onOpenVideo)
    LaunchedEffect(Unit) { vm.holder.skipNext.collect { open(it) } }
    LaunchedEffect(pauseMain) { if (pauseMain) vm.holder.player.pause() }
}

/** Mini player ala YouTube: tampil di atas bilah navigasi saat video dikecilkan; ketuk = buka lagi, X = tutup. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MiniPlayerBar(onOpen: (String) -> Unit, vm: MiniPlayerViewModel = hiltViewModel()) {
    val holder = vm.holder
    val player = holder.player
    var mediaId by remember { mutableStateOf(player.currentMediaItem?.mediaId) }
    var title by remember { mutableStateOf(player.mediaMetadata.title?.toString().orEmpty()) }
    var author by remember { mutableStateOf(player.mediaMetadata.artist?.toString().orEmpty()) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var state by remember { mutableIntStateOf(player.playbackState) }
    var frac by remember { mutableFloatStateOf(0f) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                mediaId = p.currentMediaItem?.mediaId
                title = p.mediaMetadata.title?.toString().orEmpty()
                author = p.mediaMetadata.artist?.toString().orEmpty()
                playing = p.isPlaying
                state = p.playbackState
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(mediaId) {
        while (true) {
            val d = player.duration
            frac = if (d > 0 && d != C.TIME_UNSET) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    val id = mediaId
    if (id == null || state == Player.STATE_IDLE) return
    val ended = state == Player.STATE_ENDED
    Column(Modifier.fillMaxWidth().background(YgSurface)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(0.2f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(YtRed))
        }
        Row(Modifier.fillMaxWidth().height(60.dp).clickable { onOpen(id) }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(start = 8.dp).width(96.dp).aspectRatio(16f / 9f).background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            this.player = player
                        }
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (author.isNotBlank()) Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis, color = YgMuted, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { if (ended) { player.seekTo(0); player.play() } else if (playing) player.pause() else player.play() }) {
                Icon(if (ended) Icons.Default.Replay else if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Putar/jeda")
            }
            IconButton(onClick = { holder.stopIfCurrent(id) }) { Icon(Icons.Default.Close, "Tutup") }
        }
    }
}
