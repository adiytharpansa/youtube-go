package com.app.youtubego.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.app.youtubego.presentation.common.formatDuration
import com.app.youtubego.presentation.theme.YgAccent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val YtRed = YgAccent

/** Cuplikan state ExoPlayer yang dibaca Compose (diperbarui lewat Player.Listener). */
@Stable
internal class PlayerSnapshot(player: Player) {
    var isPlaying by mutableStateOf(player.isPlaying)
    var state by mutableIntStateOf(player.playbackState)
    var tracks by mutableStateOf(player.currentTracks)
    var speed by mutableFloatStateOf(player.playbackParameters.speed)
}

@Composable
internal fun rememberPlayerSnapshot(player: Player): PlayerSnapshot {
    val snap = remember(player) { PlayerSnapshot(player) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { snap.isPlaying = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) { snap.state = playbackState }
            override fun onTracksChanged(tracks: Tracks) { snap.tracks = tracks }
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) { snap.speed = playbackParameters.speed }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    return snap
}

private data class SeekFlash(val dir: Int, val stamp: Long)

/**
 * Overlay kontrol ala YouTube:
 * - ketuk = tampil/sembunyi (otomatis sembunyi 3 dtk saat memutar), ketuk dua kali kiri/kanan = mundur/maju 10 dtk,
 *   ketuk dua kali tengah = putar/jeda, tahan = kecepatan 2x selama ditahan
 * - seekbar merah dengan bagian buffer, garis progres tipis saat kontrol tersembunyi
 * - tombol CC, PiP, pengaturan (kualitas/kecepatan/teks), layar penuh, ulangi di akhir video
 */
@Composable
internal fun YtPlayerOverlay(
    player: Player,
    snap: PlayerSnapshot,
    title: String,
    isFullscreen: Boolean,
    hasCaptions: Boolean,
    captionsOn: Boolean,
    canPip: Boolean,
    onToggleCaptions: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFrac by remember { mutableFloatStateOf(0f) }
    var flash by remember { mutableStateOf<SeekFlash?>(null) }
    var boosted by remember { mutableStateOf(false) }
    val ended = snap.state == Player.STATE_ENDED
    val buffering = snap.state == Player.STATE_BUFFERING

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            buffered = player.bufferedPosition.coerceAtLeast(0)
            duration = if (player.duration == C.TIME_UNSET) 0L else player.duration.coerceAtLeast(0)
            delay(300)
        }
    }
    LaunchedEffect(visible, interaction, snap.isPlaying, scrubbing) {
        if (visible && snap.isPlaying && !scrubbing) { delay(3000); visible = false }
    }
    LaunchedEffect(flash) { if (flash != null) { delay(650); flash = null } }
    LaunchedEffect(ended) { if (ended) visible = true }

    val posFrac = if (scrubbing) scrubFrac else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufFrac = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize()) {
        // Lapisan gestur
        Box(
            Modifier.fillMaxSize().pointerInput(player) {
                detectTapGestures(
                    onTap = { visible = !visible; interaction++ },
                    onDoubleTap = { off ->
                        val third = size.width / 3f
                        val dur = if (player.duration == C.TIME_UNSET) Long.MAX_VALUE else player.duration
                        when {
                            off.x < third -> { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); flash = SeekFlash(-1, System.nanoTime()) }
                            off.x > 2 * third -> { player.seekTo((player.currentPosition + 10_000).coerceAtMost(dur)); flash = SeekFlash(1, System.nanoTime()) }
                            else -> if (player.isPlaying) player.pause() else player.play()
                        }
                    },
                    onPress = {
                        var did = false
                        var before = 1f
                        coroutineScope {
                            val j = launch {
                                delay(500)
                                if (player.isPlaying) { did = true; before = player.playbackParameters.speed; boosted = true; player.setPlaybackSpeed(2f) }
                            }
                            tryAwaitRelease()
                            j.cancel()
                        }
                        if (did) { boosted = false; player.setPlaybackSpeed(before) }
                    },
                )
            },
        )

        // Umpan balik lompat 10 detik
        flash?.let { f ->
            Row(
                Modifier.align(if (f.dir < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 36.dp)
                    .clip(CircleShape).background(Color.Black.copy(0.5f)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (f.dir < 0) Icons.Default.FastRewind else Icons.Default.FastForward, null, tint = Color.White)
                Text(" 10 detik", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (boosted) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(CircleShape).background(Color.Black.copy(0.6f)).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(16.dp)); Text(" 2x", color = Color.White, style = MaterialTheme.typography.labelMedium) }
        }
        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White, strokeWidth = 3.dp)

        // Garis progres tipis saat kontrol tersembunyi
        if (!visible) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color.White.copy(0.25f))) {
                Box(Modifier.fillMaxWidth(bufFrac).fillMaxHeight().background(Color.White.copy(0.45f)))
                Box(Modifier.fillMaxWidth(posFrac).fillMaxHeight().background(YtRed))
            }
        }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(0.65f), Color.Transparent, Color.Transparent, Color.Black.copy(0.75f)))
                ),
            ) {
                // Bar atas
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(if (isFullscreen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.KeyboardArrowDown, "Kembali", tint = Color.White)
                    }
                    if (isFullscreen) Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    if (hasCaptions) IconButton(onClick = { onToggleCaptions(); interaction++ }) {
                        Icon(if (captionsOn) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff, "Teks", tint = Color.White)
                    }
                    if (canPip) IconButton(onClick = onEnterPip) { Icon(Icons.Default.PictureInPictureAlt, "Picture-in-picture", tint = Color.White) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Pengaturan", tint = Color.White) }
                }
                // Tombol tengah
                if (!buffering) {
                    IconButton(
                        onClick = {
                            interaction++
                            if (ended) { player.seekTo(0); player.play() } else if (player.isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier.align(Alignment.Center).size(64.dp).background(Color.Black.copy(0.45f), CircleShape),
                    ) {
                        Icon(
                            when { ended -> Icons.Default.Replay; snap.isPlaying -> Icons.Default.Pause; else -> Icons.Default.PlayArrow },
                            if (ended) "Putar ulang" else "Putar/jeda", tint = Color.White, modifier = Modifier.size(36.dp),
                        )
                    }
                }
                // Bar bawah
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = if (scrubbing) (scrubFrac * duration).toLong() else position
                        Text("${formatDuration(shown / 1000)} / ${formatDuration(duration / 1000)}", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Layar penuh", tint = Color.White)
                        }
                    }
                    YtSeekBar(
                        positionFrac = posFrac, bufferedFrac = bufFrac, enabled = duration > 0,
                        onScrubStart = { scrubbing = true },
                        onScrub = { scrubFrac = it },
                        onScrubEnd = { f -> if (duration > 0) player.seekTo((f * duration).toLong()); scrubbing = false; interaction++ },
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
            }
        }
    }
}

/** Seekbar merah ala YouTube: lintasan, bagian ter-buffer, progres, dan pegangan yang membesar saat digeser. */
@Composable
private fun YtSeekBar(
    positionFrac: Float,
    bufferedFrac: Float,
    enabled: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var active by remember { mutableStateOf(false) }
    var lastFrac by remember { mutableFloatStateOf(0f) }
    val thumb by animateDpAsState(if (active) 16.dp else 12.dp, label = "thumb")
    Box(
        modifier.fillMaxWidth().height(28.dp).onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { o -> onScrubEnd((o.x / size.width).coerceIn(0f, 1f)) })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> active = true; onScrubStart(); lastFrac = (o.x / size.width).coerceIn(0f, 1f); onScrub(lastFrac) },
                    onHorizontalDrag = { change, _ -> change.consume(); lastFrac = (change.position.x / size.width).coerceIn(0f, 1f); onScrub(lastFrac) },
                    onDragEnd = { active = false; onScrubEnd(lastFrac) },
                    onDragCancel = { active = false; onScrubEnd(lastFrac) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(0.3f)))
        Box(Modifier.fillMaxWidth(bufferedFrac.coerceIn(0f, 1f)).height(3.dp).background(Color.White.copy(0.5f)))
        Box(Modifier.fillMaxWidth(positionFrac.coerceIn(0f, 1f)).height(3.dp).background(YtRed))
        Box(
            Modifier.offset { IntOffset((widthPx * positionFrac.coerceIn(0f, 1f)).roundToInt() - thumb.roundToPx() / 2, 0) }
                .size(thumb).background(YtRed, CircleShape),
        )
    }
}
