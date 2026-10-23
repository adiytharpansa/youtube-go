package com.app.playtubelite.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.app.playtubelite.domain.model.Chapter
import com.app.playtubelite.domain.model.Storyboard
import com.app.playtubelite.playback.findComponentActivity
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
import com.app.playtubelite.presentation.common.formatDuration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val YtRed = Color(0xFFFF0000)

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

private data class SeekFlash(val dir: Int, val stamp: Long, val total: Int)

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
    onNext: (() -> Unit)? = null,
    storyboard: Storyboard? = null,
    chapters: List<Chapter> = emptyList(),
    onOpenChapters: (() -> Unit)? = null,
    /** Video sebelumnya di playlist (null = tidak ada: tombol hanya mengulang dari awal). */
    onPrevious: (() -> Unit)? = null,
    /** Cubit dua jari: true = isi layar (zoom), false = sesuai layar (seperti YouTube). */
    onPinchZoom: (Boolean) -> Unit = {},
    /** Geser turun di portrait: delta px tiap gerakan; layar pemutar mengikuti jari lalu memutuskan kecilkan saat dilepas. */
    onMinimizeDrag: (Float) -> Unit = {},
    onMinimizeDragEnd: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var live by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFrac by remember { mutableFloatStateOf(0f) }
    var flash by remember { mutableStateOf<SeekFlash?>(null) }
    var boosted by remember { mutableStateOf(false) }
    val ended = snap.state == Player.STATE_ENDED
    val currentFlash by rememberUpdatedState(flash)
    val ctx = LocalContext.current
    val act = remember(ctx) { ctx.findComponentActivity() }
    val audio = remember(ctx) { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var hud by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var hudStamp by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudStamp) { if (hudStamp > 0) { delay(900); hud = null } }
    fun brightnessNow(): Float = runCatching {
        act?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
            ?: (Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
    }.getOrDefault(0.5f)
    fun setBrightness(v: Float) { act?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = v.coerceIn(0.02f, 1f) } } }
    fun volumeNow(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    fun setVolume(v: Float) { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0) }
    val fullscreenNow by rememberUpdatedState(isFullscreen)
    val onBackNow by rememberUpdatedState(onBack)
    val onFullscreenNow by rememberUpdatedState(onToggleFullscreen)
    val onPinchNow by rememberUpdatedState(onPinchZoom)
    val onMinDragNow by rememberUpdatedState(onMinimizeDrag)
    val onMinEndNow by rememberUpdatedState(onMinimizeDragEnd)
    var pinchLabel by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }
    LaunchedEffect(isFullscreen) { if (!isFullscreen) locked = false }
    LaunchedEffect(pinchLabel) { if (pinchLabel != null) { delay(900); pinchLabel = null } }
    /** Lompat 10 dtk per ketukan; ketukan beruntun ke arah sama menumpuk (10 -> 20 -> 30 dtk) seperti YouTube. */
    fun doSeek(dir: Int) {
        val dur = if (player.duration == C.TIME_UNSET) Long.MAX_VALUE else player.duration
        player.seekTo((player.currentPosition + dir * 10_000L).coerceIn(0L, dur))
        val prev = currentFlash
        val total = if (prev != null && prev.dir == dir) prev.total + 10 else 10
        flash = SeekFlash(dir, System.nanoTime(), total)
        interaction++
    }
    val buffering = snap.state == Player.STATE_BUFFERING

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            buffered = player.bufferedPosition.coerceAtLeast(0)
            duration = if (player.duration == C.TIME_UNSET) 0L else player.duration.coerceAtLeast(0)
            live = player.isCurrentMediaItemLive
            // Seekbar halus saat kontrol tampil & memutar (10x/dtk); hemat CPU saat tersembunyi/dijeda.
            delay(if (visible && snap.isPlaying) 100L else 400L)
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
                    onTap = { off ->
                        val third = size.width / 3f
                        val side = when { off.x < third -> -1; off.x > 2 * third -> 1; else -> 0 }
                        val f = currentFlash
                        // Sedang beruntun melompat: ketukan tunggal berikutnya di sisi yang sama melanjutkan lompatan.
                        if (f != null && side != 0 && side == f.dir) doSeek(side) else { visible = !visible; interaction++ }
                    },
                    onDoubleTap = { off ->
                        val third = size.width / 3f
                        when {
                            off.x < third -> doSeek(-1)
                            off.x > 2 * third -> doSeek(1)
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
            }.pointerInput(player) {
                // Portrait: geser naik = layar penuh. Layar penuh: geser kiri = kecerahan, geser kanan = volume (seperti YouTube).
                var acc = 0f
                var left = true
                var startVal = 0f
                detectVerticalDragGestures(
                    onDragStart = { o ->
                        acc = 0f
                        left = o.x < size.width / 2f
                        if (fullscreenNow) startVal = if (left) brightnessNow() else volumeNow()
                    },
                    onVerticalDrag = { change, d ->
                        change.consume(); acc += d
                        if (fullscreenNow) {
                            val v = (startVal - acc / size.height * 1.4f).coerceIn(0f, 1f)
                            if (left) { setBrightness(v); hud = "Kecerahan" to v } else { setVolume(v); hud = "Volume" to v }
                            hudStamp++
                        } else if (acc > 0f) onMinDragNow(d) // portrait: turun = layar ikut turun (mengecil)
                    },
                    onDragEnd = {
                        if (!fullscreenNow) {
                            if (acc < -90f) onFullscreenNow() else onMinEndNow() // turun: lepas lewat ambang = kecilkan, selain itu kembali
                        }
                    },
                    onDragCancel = { if (!fullscreenNow) onMinEndNow() },
                )
            }.pointerInput(player) {
                // Cubit dua jari: lebarkan = isi layar, rapatkan = sesuai layar. Hanya bereaksi bila >= 2 jari.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var scale = 1f
                    var fired = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2 && !fired) {
                            scale *= event.calculateZoom()
                            event.changes.forEach { it.consume() }
                            if (scale > 1.15f) { onPinchNow(true); pinchLabel = "Isi layar"; fired = true }
                            else if (scale < 0.87f) { onPinchNow(false); pinchLabel = "Sesuai layar"; fired = true }
                        }
                    } while (event.changes.any { it.pressed })
                }
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
                Text(" ${f.total} detik", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (boosted) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(CircleShape).background(Color.Black.copy(0.6f)).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(16.dp)); Text(" 2x", color = Color.White, style = MaterialTheme.typography.labelMedium) }
        }
        pinchLabel?.let { label ->
            Text(
                label, color = Color.White, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(CircleShape).background(Color.Black.copy(0.6f)).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        hud?.let { (label, v) ->
            Column(
                Modifier.align(Alignment.Center).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(Color.Black.copy(0.65f)).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$label ${(v * 100).roundToInt()}%", color = Color.White, style = MaterialTheme.typography.labelLarge)
                Box(Modifier.padding(top = 6.dp).width(110.dp).height(3.dp).background(Color.White.copy(0.3f))) { Box(Modifier.fillMaxWidth(v).fillMaxHeight().background(Color.White)) }
            }
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
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)) else Modifier)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(if (isFullscreen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.KeyboardArrowDown, "Kembali", tint = Color.White)
                    }
                    if (isFullscreen) Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    if (isFullscreen) IconButton(onClick = { locked = true; visible = false }) { Icon(Icons.Default.Lock, "Kunci layar", tint = Color.White) }
                    if (hasCaptions) IconButton(onClick = { onToggleCaptions(); interaction++ }) {
                        Icon(if (captionsOn) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionOff, "Teks", tint = Color.White)
                    }
                    if (canPip) IconButton(onClick = onEnterPip) { Icon(Icons.Default.PictureInPictureAlt, "Picture-in-picture", tint = Color.White) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Pengaturan", tint = Color.White) }
                }
                // Tombol tengah: sebelumnya (ulang dari awal) - putar/jeda - berikutnya
                if (scrubbing) {
                    val scrubMs = (scrubFrac * duration).toLong()
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (storyboard != null) StoryboardPreview(storyboard, scrubMs)
                        Text(
                            formatDuration(scrubMs / 1000), color = Color.White,
                            style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp).clip(CircleShape).background(Color.Black.copy(0.55f)).padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                        chapters.lastOrNull { it.startMs <= scrubMs }?.title?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp).widthIn(max = 240.dp))
                        }
                    }
                } else if (!buffering) {
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        IconButton(
                            onClick = { interaction++; if (position > 3_000L || onPrevious == null) player.seekTo(0) else onPrevious() },
                            enabled = position > 3_000L || onPrevious != null,
                            modifier = Modifier.size(48.dp).alpha(if (position > 3_000L || onPrevious != null) 1f else 0.4f).background(Color.Black.copy(0.35f), CircleShape),
                        ) { Icon(Icons.Default.SkipPrevious, if (position > 3_000L || onPrevious == null) "Dari awal" else "Video sebelumnya", tint = Color.White, modifier = Modifier.size(28.dp)) }
                        IconButton(
                            onClick = {
                                interaction++
                                if (ended) { player.seekTo(0); player.play() } else if (player.isPlaying) player.pause() else player.play()
                            },
                            modifier = Modifier.size(64.dp).background(Color.Black.copy(0.45f), CircleShape),
                        ) {
                            Icon(
                                when { ended -> Icons.Default.Replay; snap.isPlaying -> Icons.Default.Pause; else -> Icons.Default.PlayArrow },
                                if (ended) "Putar ulang" else "Putar/jeda", tint = Color.White, modifier = Modifier.size(36.dp),
                            )
                        }
                        IconButton(
                            onClick = { onNext?.invoke() }, enabled = onNext != null,
                            modifier = Modifier.size(48.dp).alpha(if (onNext != null) 1f else 0.4f).background(Color.Black.copy(0.35f), CircleShape),
                        ) { Icon(Icons.Default.SkipNext, "Video berikutnya", tint = Color.White, modifier = Modifier.size(28.dp)) }
                    }
                }
                // Bar bawah
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .then(if (isFullscreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)).padding(bottom = 10.dp) else Modifier)
                        .padding(start = 14.dp, end = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = if (scrubbing) (scrubFrac * duration).toLong() else position
                        val chapterTitle = chapters.lastOrNull { it.startMs <= shown }?.title.orEmpty()
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (live) "\u25CF LANGSUNG" else "${formatDuration(shown / 1000)} / ${formatDuration(duration / 1000)}",
                                color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            )
                            if (chapterTitle.isNotBlank()) Text(
                                "  •  $chapterTitle" + if (onOpenChapters != null && chapters.size > 1) "  ›" else "",
                                color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                                    .clickable(enabled = onOpenChapters != null && chapters.size > 1) { onOpenChapters?.invoke(); interaction++ },
                            )
                        }
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Layar penuh", tint = Color.White)
                        }
                    }
                    YtSeekBar(
                        positionFrac = posFrac, bufferedFrac = bufFrac, enabled = duration > 0,
                        chapterFracs = if (duration > 0) chapters.drop(1).map { (it.startMs.toFloat() / duration).coerceIn(0f, 1f) } else emptyList(),
                        onScrubStart = { scrubbing = true },
                        onScrub = { scrubFrac = it },
                        onScrubEnd = { f -> if (duration > 0) player.seekTo((f * duration).toLong()); scrubbing = false; interaction++ },
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
            }
        }

        // Kunci layar (layar penuh): semua gestur & kontrol dinonaktifkan; ketuk memunculkan tombol buka kunci sesaat.
        if (locked && isFullscreen) {
            var showUnlock by remember { mutableStateOf(true) }
            var unlockStamp by remember { mutableIntStateOf(0) }
            LaunchedEffect(showUnlock, unlockStamp) { if (showUnlock) { delay(2500); showUnlock = false } }
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { showUnlock = true; unlockStamp++ } }) {
                if (showUnlock) IconButton(
                    onClick = { locked = false; visible = true; interaction++ },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp).background(Color.Black.copy(0.6f), CircleShape),
                ) { Icon(Icons.Default.LockOpen, "Buka kunci", tint = Color.White) }
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
    chapterFracs: List<Float>,
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
        modifier.fillMaxWidth().height(40.dp).onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
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
        chapterFracs.forEach { f ->
            Box(Modifier.offset { IntOffset((widthPx * f).roundToInt() - 1, 0) }.width(2.dp).height(3.dp).background(Color.Black.copy(0.75f)))
        }
        Box(
            Modifier.offset { IntOffset((widthPx * positionFrac.coerceIn(0f, 1f)).roundToInt() - thumb.roundToPx() / 2, 0) }
                .size(thumb).background(YtRed, CircleShape),
        )
    }
}

/** Pratinjau bingkai saat menggeser seekbar: memotong satu bingkai dari gambar sprite storyboard YouTube. */
@Composable
private fun StoryboardPreview(sb: Storyboard, posMs: Long) {
    val perPage = (sb.perX * sb.perY).coerceAtLeast(1)
    val idx = (posMs / sb.durationPerFrameMs.coerceAtLeast(1)).toInt().coerceIn(0, sb.total - 1)
    val page = (idx / perPage).coerceAtMost(sb.urls.lastIndex)
    val within = idx - page * perPage
    val col = within % sb.perX
    val row = within / sb.perX
    val framesOnPage = minOf(perPage, sb.total - page * perPage).coerceAtLeast(1)
    val cols = minOf(sb.perX, framesOnPage)
    val rows = (framesOnPage + sb.perX - 1) / sb.perX
    val previewW = 150.dp
    val scale = previewW.value / sb.frameW
    val previewH = (sb.frameH * scale).dp
    Box(Modifier.size(previewW, previewH).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(Color.Black).clipToBounds(), contentAlignment = Alignment.TopStart) {
        AsyncImage(
            model = sb.urls[page], contentDescription = null, contentScale = ContentScale.FillBounds,
            modifier = Modifier.requiredSize((cols * sb.frameW * scale).dp, (rows * sb.frameH * scale).dp)
                .offset((-col * sb.frameW * scale).dp, (-row * sb.frameH * scale).dp),
        )
    }
}
