package com.app.youtubego.presentation.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State as LifecycleState
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.app.youtubego.R
import com.app.youtubego.data.newpipe.DevicePlayback
import com.app.youtubego.domain.model.CaptionTrack
import com.app.youtubego.domain.model.CommentItem
import com.app.youtubego.domain.model.StreamOption
import com.app.youtubego.domain.model.VideoDetail
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.library.LibraryViewModel
import com.app.youtubego.presentation.library.PlaylistPickerDialog
import com.app.youtubego.presentation.library.VideoItem
import com.app.youtubego.data.local.DownloadStatus
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.app.youtubego.presentation.theme.ForceLightStatusBarIcons
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurface
import com.app.youtubego.presentation.theme.YgSurfaceVariant
import com.app.youtubego.playback.BackgroundPlaybackEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private enum class PlayerSheet { SETTINGS, COMMENTS, DESCRIPTION, CHAPTERS }
private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val CAPTION_SIZE_NAMES = listOf("Kecil", "Normal", "Besar", "Sangat besar")
private val CAPTION_SCALES = floatArrayOf(0.75f, 1f, 1.3f, 1.7f)
private val CAPTION_STYLE_NAMES = listOf("Latar transparan", "Tanpa latar", "Teks kuning", "Latar hitam penuh")

@androidx.annotation.OptIn(UnstableApi::class)
private fun captionStyleOf(i: Int): CaptionStyleCompat {
    val white = 0xFFFFFFFF.toInt(); val black = 0xFF000000.toInt(); val clear = 0
    return when (i) {
        1 -> CaptionStyleCompat(white, clear, clear, CaptionStyleCompat.EDGE_TYPE_OUTLINE, black, null)
        2 -> CaptionStyleCompat(0xFFFFEB3B.toInt(), 0x99000000.toInt(), clear, CaptionStyleCompat.EDGE_TYPE_NONE, black, null)
        3 -> CaptionStyleCompat(white, black, clear, CaptionStyleCompat.EDGE_TYPE_NONE, black, null)
        else -> CaptionStyleCompat(white, 0x99000000.toInt(), clear, CaptionStyleCompat.EDGE_TYPE_NONE, black, null)
    }
}

/**
 * Halaman tonton ala YouTube.
 * Pemutar: kontrol kustom (ketuk/ketuk-dua-kali/tahan 2x, seekbar merah, CC, kualitas, kecepatan, PiP,
 * layar penuh otomatis saat HP diputar, putar otomatis video berikutnya dengan hitung mundur).
 * Di bawah pemutar: judul + "selengkapnya", channel + Berlangganan, Suka/Bagikan/Simpan, komentar, video berikutnya.
 * Hemat data tetap: batas awal 720p, pilihan kualitas lebih tinggi manual lewat Pengaturan.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoId: String, onBack: () -> Unit, onVideoClick: (String) -> Unit, onChannelClick: (String) -> Unit = {},
    vm: PlayerViewModel = hiltViewModel(), libVm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val device by vm.device.collectAsState()
    val deviceSettled by vm.deviceSettled.collectAsState()
    val comments by vm.comments.collectAsState()
    val likedIds by vm.liked.collectAsState()
    val savedIds by vm.saved.collectAsState()
    val subscribedIds by vm.subscribed.collectAsState()
    val autoplay by vm.autoplay.collectAsState()
    val highQuality by vm.highQuality.collectAsState()
    val backgroundPlay by vm.backgroundPlay.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val captionSize by vm.captionSize.collectAsState()
    val captionStyle by vm.captionStyle.collectAsState()
    val downloadItems by vm.downloadItems.collectAsState()
    var playlistDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val orientation = LocalConfiguration.current.orientation
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var adaptive by remember(videoId) { mutableStateOf(false) }
    var selectedHeight by remember(videoId) { mutableStateOf<Int?>(null) }
    var selectedStreamUrl by remember(videoId) { mutableStateOf<String?>(null) }
    /** Tinggi video pilihan pengguna untuk sumber video-saja + audio-saja (null = Otomatis). */
    var mergedPick by remember(videoId) { mutableStateOf<Int?>(null) }
    var captionIndex by remember(videoId) { mutableIntStateOf(-1) }
    var loopOn by remember(videoId) { mutableStateOf(false) }
    var zoomFill by rememberSaveable { mutableStateOf(false) }

    // Pemutar BERSAMA (singleton, dipegang PlaybackService): tidak dibuat/dibuang per layar, jadi pindah video
    // tidak memutus koneksi & decoder, dan suara bisa lanjut di latar belakang.
    val holder = vm.holder
    val player = holder.player
    val snap = rememberPlayerSnapshot(player)
    val queue by holder.queue.collectAsState()
    // Video ini sedang diputar: keluarkan dari antrean (seperti YouTube).
    LaunchedEffect(videoId) { holder.removeFromQueue(videoId) }
    val run by holder.playlistRun.collectAsState()
    val runNow = run
    val runIndex = runNow?.videos?.indexOfFirst { it.id == videoId } ?: -1
    // Pindah ke video di luar playlist (mis. dari rekomendasi): keluar dari mode playlist.
    LaunchedEffect(videoId, runNow) { if (runNow != null && runIndex < 0) holder.clearPlaylist() }
    var shareAt by remember { mutableStateOf<Pair<String, Long>?>(null) }
    // Geser turun di pemutar (portrait): layar ikut turun & mengecil mengikuti jari; lepas lewat ambang = kecilkan.
    ForceLightStatusBarIcons()
    val haptic = LocalHapticFeedback.current
    fun tick() = haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    val dragAnim = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    val minimizeThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    // ON_STOP (layar mati / ke Beranda): suara lanjut bila "Putar di latar belakang" aktif, selain itu jeda.
    BackgroundPlaybackEffect(holder, backgroundPlay)
    // Penanda layar pemutar aktif (hitungan, karena pindah video sesaat menyusun dua layar): putar otomatis di
    // latar belakang diurus PlayerHolder, di layar ini diurus hitung mundur 5 dtk.
    DisposableEffect(holder) { holder.playerScreens.incrementAndGet(); holder.dropPendingNext(); onDispose { holder.playerScreens.decrementAndGet() } }
    DisposableEffect(videoId) {
        onDispose {
            // Pulihkan orientasi & system bars bila layar ditinggalkan saat fullscreen.
            activity?.let {
                it.window.attributes = it.window.attributes.apply { screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            // Keluar dari layar ini (kembali/pindah) = hentikan. Activity DIHANCURKAN (mis. digeser dari recent)
            // BUKAN keluar layar: biarkan service melanjutkan suara.
            val destroyed = (activity as? ComponentActivity)?.lifecycle?.currentState == LifecycleState.DESTROYED
            // Ditutup lewat "kecilkan" (mini player): biarkan pemutar tetap memuat video ini.
            val minimized = holder.minimizeOnLeave
            holder.minimizeOnLeave = false
            if (!destroyed && !minimized) holder.stopIfCurrent(videoId)
        }
    }
    // Picture-in-Picture: otomatis saat pengguna meninggalkan aplikasi sambil memutar.
    // Jendela PiP: rasio mengikuti video, tombol putar/jeda di jendela, dan (Android 12+) masuk PiP mulus saat gestur Beranda.
    DisposableEffect(activity, player) {
        val a = activity as? ComponentActivity
        // Android 12+ masuk PiP sendiri lewat setAutoEnterEnabled; di bawahnya lewat onUserLeaveHint.
        val leave = Runnable { if (player.isPlaying && Build.VERSION.SDK_INT < 31) enterPip(a, player) }
        val pipChanged = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        fun refreshPip() { if (a != null) updatePipParams(a, player) }
        val pipListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = refreshPip()
            override fun onVideoSizeChanged(videoSize: VideoSize) = refreshPip()
        }
        val toggleReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action == PIP_TOGGLE_ACTION) { if (player.isPlaying) player.pause() else player.play() }
            }
        }
        if (a != null) {
            ContextCompat.registerReceiver(a, toggleReceiver, IntentFilter(PIP_TOGGLE_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            refreshPip()
        }
        a?.addOnUserLeaveHintListener(leave)
        a?.addOnPictureInPictureModeChangedListener(pipChanged)
        player.addListener(pipListener)
        onDispose {
            player.removeListener(pipListener)
            a?.removeOnUserLeaveHintListener(leave)
            a?.removeOnPictureInPictureModeChangedListener(pipChanged)
            if (a != null) { runCatching { a.unregisterReceiver(toggleReceiver) }; clearPipAutoEnter(a) }
        }
    }
    var playerError by remember(videoId) { mutableStateOf<String?>(null) }
    var autoRetries by remember(videoId) { mutableIntStateOf(0) }
    /** Indeks sumber putar yang sedang dicoba (0 = prioritas utama). Naik tiap sumber gagal, supaya tidak mengulang URL yang sama. */
    var sourceSkip by remember(videoId) { mutableIntStateOf(0) }
    /** Kode HTTP terakhir yang ditolak server (mis. 403/500/505), untuk pesan galat yang bisa dilacak. */
    var lastHttp by remember(videoId) { mutableStateOf<Int?>(null) }
    DisposableEffect(player, videoId) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) autoRetries = 0
            }
            override fun onPlayerError(error: PlaybackException) {
                // Jaringan putus / URL kedaluwarsa / ditolak server: coba URL SEGAR sekali, lalu PINDAH ke sumber
                // berikutnya (perangkat -> proksi server) alih-alih mengulang URL yang sama dan menyerah.
                val http = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                if (http != null) lastHttp = http
                val failHost = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.dataSpec?.uri?.host
                Log.w("YGO_PLAYER", "putar gagal: kode=${error.errorCodeName} http=${http ?: "-"} host=${failHost ?: "-"} sumberKe=$sourceSkip")
                val recoverable = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    error.errorCode in 3000..3999 // galat parsing manifest/kontainer
                if (recoverable && player.currentMediaItem?.mediaId == videoId) {
                    if (autoRetries < 1) { autoRetries++; vm.retry(); return }
                    if (sourceSkip < 5) { sourceSkip++; return } // efek di bawah memuat sumber berikutnya
                }
                playerError = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "Aliran video gagal diambil dari server (galat HTTP${lastHttp?.let { " $it" }.orEmpty()}). Semua sumber sudah dicoba — coba lagi sebentar."
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
    /** Kembali dari layar pemutar = KECILKAN ke mini player (seperti YouTube), kecuali video belum dimuat/sudah selesai. */
    val minimize: () -> Unit = {
        val st = player.playbackState
        if (player.currentMediaItem?.mediaId == videoId && st != Player.STATE_IDLE && st != Player.STATE_ENDED) holder.minimizeOnLeave = true
        onBack()
    }
    BackHandler(enabled = !isFullscreen && !inPip) { minimize() }
    // HP diputar ke landscape = layar penuh, kembali portrait = keluar layar penuh (seperti YouTube).
    LaunchedEffect(orientation) { isFullscreen = orientation == Configuration.ORIENTATION_LANDSCAPE }
    LaunchedEffect(isFullscreen) {
        val act = activity ?: return@LaunchedEffect
        val ctrl = WindowCompat.getInsetsController(act.window, act.window.decorView)
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isFullscreen) {
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            if (orientation != Configuration.ORIENTATION_LANDSCAPE) act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ctrl.show(WindowInsetsCompat.Type.systemBars())
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) { act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; delay(700) }
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val detail = (state as? UiState.Success)?.data
    val captions = remember(detail, device) { detail?.captions.orEmpty().ifEmpty { device?.captions.orEmpty() }.filter { it.url.isNotBlank() } }
    val prevVideo = if (runNow != null && runIndex > 0) runNow.videos[runIndex - 1] else null
    val nextVideo = queue.firstOrNull { it.id != videoId }
        ?: (if (runNow != null && runIndex >= 0) runNow.videos.getOrNull(runIndex + 1) else detail?.recommendations?.firstOrNull { it.id != videoId })
    val ended = snap.state == Player.STATE_ENDED
    LaunchedEffect(nextVideo?.id) { holder.upNextId = nextVideo?.id }
    LaunchedEffect(prevVideo?.id) { holder.upPrevId = prevVideo?.id }
    var autoCancelled by remember(videoId) { mutableStateOf(false) }
    var countdown by remember(videoId) { mutableIntStateOf(5) }
    LaunchedEffect(ended, autoplay, nextVideo?.id, autoCancelled) {
        if (ended && autoplay && nextVideo != null && !autoCancelled) {
            countdown = 5
            while (countdown > 0) { delay(1000); countdown-- }
            onVideoClick(nextVideo.id)
        }
    }

    // Siapkan media per URL segar (di luar cabang UI agar rotasi/layar penuh tidak mengulang putaran).
    // Mulai SEGERA begitu ekstraksi perangkat punya stream; detail API hanya ditunggu paling lama 1,2 dtk
    // (untuk takarir) -- tidak lagi memblokir putar sampai server proksi menjawab. Urutan sumber: DASH perangkat,
    // stream gabungan perangkat, HLS perangkat, lalu cadangan server: gabungan, DASH, HLS.
    // Hanya memuat ulang bila URL BERUBAH, dan membawa posisi terakhir: tidak ada lagi "restart dari 0"
    // saat detail API datang belakangan atau pengaturan kualitas berubah.
    var loadedKey by remember(videoId) { mutableStateOf<String?>(null) }
    LaunchedEffect(deviceSettled, device, highQuality, sourceSkip, mergedPick, state is UiState.Loading, detail?.combinedStreams?.firstOrNull()?.url, detail?.dashUrl, detail?.hlsUrl) {
        if (!deviceSettled) return@LaunchedEffect
        if (selectedStreamUrl != null) return@LaunchedEffect // pengguna memilih kualitas manual: jangan ditimpa
        val dev = device
        val devHasStream = dev != null && (!dev.dashUrl.isNullOrBlank() || dev.combined.isNotEmpty() || !dev.hlsUrl.isNullOrBlank())
        if (!devHasStream && state is UiState.Loading) return@LaunchedEffect // tunggu API; efek diulang saat detail tiba
        if (devHasStream && state is UiState.Loading) {
            // Detail kini berbagi unduhan dengan ekstraksi perangkat sehingga tiba hampir bersamaan; tunggu singkat saja (untuk takarir).
            withTimeoutOrNull(400) { snapshotFlow { state is UiState.Loading }.first { !it } }
        }
        val d = (state as? UiState.Success)?.data
        val devDash = dev?.dashUrl
        val devCombined = (if (highQuality) dev?.combined?.lastOrNull() else dev?.combined?.firstOrNull())?.url
        val devHls = dev?.hlsUrl
        val combined = (if (highQuality) d?.combinedStreams?.lastOrNull() else d?.combinedStreams?.firstOrNull())?.url
        val subs = d?.captions.orEmpty().ifEmpty { dev?.captions.orEmpty() }.filter { it.url.isNotBlank() }
        val summary = d?.summary ?: dev?.summary
        // Daftar sumber berurutan prioritas; sourceSkip memilih yang ke-n bila sumber sebelumnya gagal diputar.
        // Sumber PALING ATAS: video-saja + audio-saja digabung (480p-1080p). YouTube hampir tak pernah memberi manifes DASH
        // untuk video biasa dan stream gabungannya mentok 360p, jadi tanpa ini pemutar selalu 360p. Dipakai hanya bila
        // resolusi yang dipilih lebih tinggi dari stream gabungan terbaik (atau pengguna memilih tinggi tertentu).
        val mergedOpts = mergedVideoOptions(dev)
        val mergedAudio = pickAudio(dev)
        val muxedBestH = dev?.combined?.maxOfOrNull { heightOf(it) } ?: 0
        val targetH = mergedPick ?: holder.autoHeight()
        val mergedVideo = mergedOpts.filter { heightOf(it) <= targetH }.maxByOrNull { heightOf(it) } ?: mergedOpts.minByOrNull { heightOf(it) }
        val useMerged = mergedVideo != null && mergedAudio != null && (mergedPick != null || heightOf(mergedVideo) > muxedBestH)
        val candidates = listOfNotNull(
            mergedVideo?.takeIf { useMerged }?.let { PlaySource(it.url, null, false, mergedAudio) },
            devDash?.takeIf { it.isNotBlank() }?.let { PlaySource(it, MimeTypes.APPLICATION_MPD, true) },
            devCombined?.takeIf { it.isNotBlank() }?.let { PlaySource(it, null, false) },
            devHls?.takeIf { it.isNotBlank() }?.let { PlaySource(it, MimeTypes.APPLICATION_M3U8, true) },
            combined?.takeIf { it.isNotBlank() }?.let { PlaySource(it, null, false) },
            d?.dashUrl?.takeIf { it.isNotBlank() }?.let { PlaySource(it, MimeTypes.APPLICATION_MPD, true) },
            d?.hlsUrl?.takeIf { it.isNotBlank() }?.let { PlaySource(it, MimeTypes.APPLICATION_M3U8, true) },
        ).distinctBy { it.uri }
        val pick = candidates.getOrNull(sourceSkip)
        val uri: String? = pick?.uri
        val mime: String? = pick?.mime
        val isAdaptive: Boolean = pick?.adaptive ?: false
        if (uri == null) {
            if (state !is UiState.Error) playerError = if (sourceSkip > 0)
                "Semua sumber video sudah dicoba tapi gagal${lastHttp?.let { " (HTTP $it)" }.orEmpty()}. Coba lagi nanti atau pilih video lain."
            else "Format putar untuk video ini sedang tidak tersedia dari jalur mana pun. Coba lagi nanti atau pilih video lain."
            return@LaunchedEffect
        }
        if (loadedKey == uri) return@LaunchedEffect
        adaptive = isAdaptive
        playerError = null
        val item = buildItem(videoId, uri, mime, subs, summary?.title, summary?.author, summary?.thumbnailUrl)
        if (loadedKey == null && holder.isCurrent(videoId, uri)) { loadedKey = uri; return@LaunchedEffect }
        val sameVideoReload = loadedKey != null
        val started = (activity as? ComponentActivity)?.lifecycle?.currentState?.isAtLeast(LifecycleState.STARTED) != false
        Log.d("YGO_PLAYER", "pakai sumber ke-$sourceSkip: host=${android.net.Uri.parse(uri).host ?: "-"} mime=${mime ?: "progresif"}")
        holder.play(
            item,
            startPositionMs = if (sameVideoReload) player.currentPosition.coerceAtLeast(0L) else null,
            keepSettings = sameVideoReload,
            videoEnabled = started,
            audioUrl = pick?.audioUrl,
        )
        loadedKey = uri
    }

    // ---- Pilihan kualitas, kecepatan, teks ----
    val adaptiveHeights = remember(snap.tracks) {
        snap.tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g.getTrackFormat(it).height } }
            .filter { it > 0 }.distinct().sortedDescending()
    }
    val streamOptions: List<StreamOption> = device?.combined.orEmpty().ifEmpty { detail?.combinedStreams.orEmpty() }
    fun applyHeight(h: Int?) {
        val b = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (h == null) holder.autoMaxSize().let { b.setMaxVideoSize(it.first, it.second) }
        else {
            b.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            outer@ for (g in player.currentTracks.groups) {
                if (g.type != C.TRACK_TYPE_VIDEO) continue
                for (i in 0 until g.length) if (g.getTrackFormat(i).height == h && g.isTrackSupported(i)) {
                    b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)); break@outer
                }
            }
        }
        selectedHeight = h
        player.trackSelectionParameters = b.build()
    }
    fun applyStream(o: StreamOption) {
        selectedStreamUrl = o.url
        val pos = player.currentPosition
        val sm = detail?.summary ?: device?.summary
        holder.play(buildItem(videoId, o.url, null, captions, sm?.title, sm?.author, sm?.thumbnailUrl), startPositionMs = pos, keepSettings = true)
    }
    fun applyCaption(i: Int) {
        captionIndex = i
        val b = player.trackSelectionParameters.buildUpon()
        if (i < 0) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(captions[i].languageCode)
        player.trackSelectionParameters = b.build()
    }
    val mergedOptions = remember(device) { mergedVideoOptions(device) }
    val mergedAudioUrl = remember(device) { pickAudio(device) }
    // Tinggi video yang BENAR-BENAR diputar (dari trek terpilih), untuk label "Otomatis (720p)" seperti YouTube.
    val playingHeight = remember(snap.tracks) {
        snap.tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.let { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it).height } } ?: 0
    }
    val qualityChoices: List<Choice> = when {
        !adaptive && mergedOptions.isNotEmpty() && mergedAudioUrl != null ->
            listOf(Choice(if (mergedPick == null && playingHeight > 0) "Otomatis (${playingHeight}p)" else "Otomatis", mergedPick == null) { sourceSkip = 0; mergedPick = null }) +
                mergedOptions.map { o -> Choice(o.qualityLabel.ifBlank { "${heightOf(o)}p" }, mergedPick == heightOf(o)) { sourceSkip = 0; mergedPick = heightOf(o) } }
        adaptive && adaptiveHeights.size > 1 -> listOf(Choice("Otomatis (maks ${holder.autoMaxSize().second}p)", selectedHeight == null) { applyHeight(null) }) +
            adaptiveHeights.map { h -> Choice("${h}p", selectedHeight == h) { applyHeight(h) } }
        !adaptive && streamOptions.size > 1 -> streamOptions.map { o ->
            Choice(o.qualityLabel.ifBlank { "Standar" }, o.url == (selectedStreamUrl ?: streamOptions.first().url)) { applyStream(o) }
        }
        else -> emptyList()
    }
    val qualityLabel = qualityChoices.firstOrNull { it.selected }?.label ?: "Otomatis"
    val speedChoices = SPEEDS.map { s -> Choice(speedText(s), snap.speed == s) { player.setPlaybackSpeed(s) } }
    val speedLabel = speedText(snap.speed)
    val captionChoices = if (captions.isEmpty()) emptyList() else
        listOf(Choice("Mati", captionIndex < 0) { applyCaption(-1) }) + captions.mapIndexed { i, c -> Choice(c.label.ifBlank { c.languageCode }, captionIndex == i) { applyCaption(i) } }
    val captionLabel = if (captionIndex < 0) "Mati" else captions.getOrNull(captionIndex)?.label.orEmpty()
    val sleepEndsAt by holder.sleepEndsAt.collectAsState()
    val sleepLabel = sleepEndsAt?.let { "${((it - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)} mnt lagi" } ?: "Mati"
    val sleepChoices = listOf(Choice("Mati", sleepEndsAt == null) { holder.setSleepTimer(null) }) +
        listOf(5, 15, 30, 45, 60).map { m -> Choice("$m menit", false) { holder.setSleepTimer(m) } }
    val canPip = remember(activity) { activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true }

    Column(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val dy = dragAnim.value
                translationY = dy
                val f = (1f - dy / 1400f).coerceIn(0.88f, 1f)
                scaleX = f; scaleY = f; transformOrigin = TransformOrigin(0.5f, 0f)
                alpha = (1f - dy / 1000f).coerceIn(0.5f, 1f)
            }
            .background(Color.Black)
            .then(if (!isFullscreen && !inPip) Modifier.statusBarsPadding() else Modifier),
    ) {
        Box(Modifier.fillMaxWidth().then(if (isFullscreen || inPip) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player; useController = false
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                // Pemutar kini berumur panjang: lepaskan PlayerView agar Activity tidak bocor lewat listener-nya.
                update = {
                    it.resizeMode = if (zoomFill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // Layar boleh mati saat dijeda (hemat baterai), tetap menyala saat memutar / buffering.
                    it.keepScreenOn = snap.isPlaying || snap.state == Player.STATE_BUFFERING
                    it.subtitleView?.let { sv ->
                        sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * CAPTION_SCALES[captionSize.coerceIn(0, 3)])
                        sv.setStyle(captionStyleOf(captionStyle))
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            if (!inPip) {
                YtPlayerOverlay(
                    player = player, snap = snap, title = detail?.summary?.title.orEmpty(), isFullscreen = isFullscreen,
                    hasCaptions = captions.isNotEmpty(), captionsOn = captionIndex >= 0, canPip = canPip,
                    onToggleCaptions = { applyCaption(if (captionIndex < 0) 0 else -1) },
                    onOpenSettings = { sheet = PlayerSheet.SETTINGS },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onBack = { if (isFullscreen) isFullscreen = false else minimize() },
                    onEnterPip = { enterPip(activity, player) },
                    onNext = nextVideo?.let { n -> { onVideoClick(n.id) } },
                    onPrevious = prevVideo?.let { p -> { onVideoClick(p.id) } },
                    storyboard = device?.storyboard, chapters = device?.chapters.orEmpty(),
                    onOpenChapters = { sheet = PlayerSheet.CHAPTERS },
                    onPinchZoom = { zoomFill = it },
                    onMinimizeDrag = { dy -> dragScope.launch { dragAnim.snapTo((dragAnim.value + dy).coerceAtLeast(0f)) } },
                    onMinimizeDragEnd = { dragScope.launch { if (dragAnim.value > minimizeThresholdPx) minimize() else dragAnim.animateTo(0f) } },
                )
            }
            // Efek loading awal: sebelum sumber video siap (pemutar masih IDLE) area video tidak lagi hitam kosong.
            // Saat buffering, spinner ditampilkan oleh overlay; saat galat, pesan galat yang tampil.
            if (!inPip && playerError == null && !ended && snap.state == Player.STATE_IDLE) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White, strokeWidth = 3.dp)
            }
            playerError?.let { msg ->
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(0.75f)).padding(16.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(msg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { playerError = null; sourceSkip = 0; autoRetries = 0; vm.retry() }) { Text("Coba lagi") }
                }
            }
            if (ended && autoplay && nextVideo != null && !autoCancelled && !inPip) {
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable(enabled = false) {}.padding(16.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Berikutnya dalam $countdown", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Text(nextVideo.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { autoCancelled = true }) { Text("Batal", color = Color.White) }
                        Button(onClick = { onVideoClick(nextVideo.id) }, colors = ButtonDefaults.buttonColors(containerColor = YtRed)) { Text("Putar sekarang") }
                    }
                }
            }
        }
        if (!isFullscreen && !inPip) {
            Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                when (val s = state) {
                    is UiState.Loading -> PlayerInfoShimmer()
                    is UiState.Error -> ErrorBox(s.message) { vm.retry() }
                    is UiState.Success -> {
                        val d = s.data
                        val liked = videoId in likedIds
                        val saved = videoId in savedIds
                        val subscribed = d.summary.authorId in subscribedIds
                        LazyColumn(Modifier.fillMaxSize()) {
                            item(key = "title") { TitleBlock(d) { sheet = PlayerSheet.DESCRIPTION } }
                            item(key = "channel") { ChannelRow(d, subscribed, onOpen = { onChannelClick(d.summary.authorId) }) { tick(); vm.toggleSubscribe(d.summary.authorId) } }
                            item(key = "actions") {
                                val downloadStatus = downloadItems.firstOrNull { it.video.id == videoId }?.status
                                ActionRow(
                                    downloadStatus = downloadStatus, onDownload = vm::download, onPlaylist = { playlistDialog = true },
                                    likeLabel = if (d.likeCount + (if (liked) 1 else 0) > 0) formatViewsId(d.likeCount + (if (liked) 1 else 0)) else stringResource(R.string.player_like),
                                    liked = liked, saved = saved,
                                    onLike = { tick(); vm.toggleLike() }, onSave = { tick(); vm.toggleSave() },
                                    onShare = {
                                        val sec = player.currentPosition / 1000
                                        // Sudah ditonton >= 5 dtk: tawarkan "Mulai dari 1:23" seperti YouTube.
                                        if (sec >= 5 && player.currentMediaItem?.mediaId == videoId) shareAt = d.summary.title to sec
                                        else shareVideo(context, d.summary.title, videoId, null)
                                    },
                                )
                            }
                            item(key = "comments") { CommentsTeaser(comments) { sheet = PlayerSheet.COMMENTS } }
                            if (queue.isNotEmpty()) {
                                item {
                                    Text(stringResource(R.string.player_queue, queue.size), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp))
                                }
                                items(queue, key = { "q_" + it.id }) { v -> QueueRow(v, current = false, onPlay = { onVideoClick(v.id) }, onRemove = { holder.removeFromQueue(v.id) }) }
                            }
                            if (runNow != null && runIndex >= 0) {
                                item {
                                    Text("${runNow.name} • ${runIndex + 1}/${runNow.videos.size}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp))
                                }
                                items(runNow.videos, key = { "p_" + it.id }) { v ->
                                    QueueRow(v, current = v.id == videoId, onPlay = { if (v.id != videoId) onVideoClick(v.id) }, onRemove = null)
                                }
                            }
                            item(key = "next_header") { NextVideosHeader(autoplay, vm::setAutoplay) }
                            items(d.recommendations, key = { it.id }) { v -> VideoItem(v, onVideoClick, onChannelClick, libVm) }
                        }
                        when (sheet) {
                            PlayerSheet.COMMENTS -> CommentsSheet(comments, onSeek = { player.seekTo(it) }) { sheet = null }
                            PlayerSheet.DESCRIPTION -> DescriptionSheet(d, onSeek = { player.seekTo(it) }) { sheet = null }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
    shareAt?.let { (title, sec) ->
        var withTime by remember(sec) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { shareAt = null },
            containerColor = YgSurface,
            title = { Text("Bagikan video") },
            text = {
                Row(Modifier.fillMaxWidth().clickable { withTime = !withTime }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withTime, onCheckedChange = { withTime = it })
                    Text("Mulai dari ${formatDuration(sec)}")
                }
            },
            confirmButton = { TextButton(onClick = { shareAt = null; shareVideo(context, title, videoId, if (withTime) sec else null) }) { Text("Bagikan") } },
            dismissButton = { TextButton(onClick = { shareAt = null }) { Text("Batal") } },
        )
    }
    if (playlistDialog) {
        PlaylistPickerDialog(
            playlists = playlists, videoId = videoId,
            onToggle = { vm.togglePlaylist(it.id) }, onCreate = vm::createPlaylist, onDismiss = { playlistDialog = false },
        )
    }
    if (sheet == PlayerSheet.CHAPTERS) {
        ChaptersSheet(device?.chapters.orEmpty(), player.currentPosition, onPick = { player.seekTo(it) }) { sheet = null }
    }
    if (sheet == PlayerSheet.SETTINGS) {
        PlayerSettingsSheet(
            qualityLabel, qualityChoices, speedLabel, speedChoices, captionLabel, captionChoices,
            loopOn = loopOn, onLoopChange = { loopOn = it; player.repeatMode = if (it) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF },
            zoomFill = zoomFill, onZoomChange = { zoomFill = it },
            backgroundPlay = backgroundPlay, onBackgroundPlayChange = vm::setBackgroundPlay,
            sleepLabel = sleepLabel, sleepChoices = sleepChoices,
            captionSizeLabel = CAPTION_SIZE_NAMES[captionSize.coerceIn(0, 3)], captionSizeChoices = CAPTION_SIZE_NAMES.mapIndexed { i, n -> Choice(n, captionSize == i) { vm.setCaptionSize(i) } },
            captionStyleLabel = CAPTION_STYLE_NAMES[captionStyle.coerceIn(0, 3)], captionStyleChoices = CAPTION_STYLE_NAMES.mapIndexed { i, n -> Choice(n, captionStyle == i) { vm.setCaptionStyle(i) } },
        ) { sheet = null }
    }
}


private fun speedText(s: Float): String = if (s == 1f) "Normal" else "${s}x".replace(".0x", "x")

private fun buildItem(
    videoId: String, uri: String, mime: String?, subs: List<CaptionTrack>,
    title: String?, author: String?, thumb: String?,
): MediaItem {
    // mediaId = id video: dipakai PlayerHolder untuk tahu siapa pemilik pemutar; metadata tampil di notifikasi media.
    val b = MediaItem.Builder().setMediaId(videoId).setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(author)
                .apply { if (!thumb.isNullOrBlank()) setArtworkUri(Uri.parse(thumb)) }.build(),
        )
    if (mime != null) b.setMimeType(mime)
    if (subs.isNotEmpty()) b.setSubtitleConfigurations(subs.map {
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url)).setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(it.languageCode).setLabel(it.label.ifBlank { it.languageCode }).setSelectionFlags(0).build()
    })
    return b.build()
}

/** Sumber putar kandidat. [audioUrl] != null = video-saja + audio-saja yang digabung (uri = URL videonya). */
private data class PlaySource(val uri: String, val mime: String?, val adaptive: Boolean, val audioUrl: String? = null)

/** Tinggi piksel dari label seperti "1080p60" -> 1080. */
private fun heightOf(o: StreamOption): Int = o.qualityLabel.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

/** Urutan kompatibilitas dekoder: H.264 paling aman, VP9 lalu lainnya, AV1 terakhir (banyak HP tak punya dekoder AV1). */
private fun codecRank(o: StreamOption): Int = when {
    o.codec.startsWith("avc") -> 0
    o.codec.startsWith("vp9") || o.codec.startsWith("vp09") -> 1
    o.codec.startsWith("av01") -> 3
    else -> 2
}

/** Satu stream video-saja terbaik per tinggi (144p..1080p), terurut dari yang tertinggi. */
private fun mergedVideoOptions(dev: DevicePlayback?): List<StreamOption> =
    dev?.videoOnly.orEmpty()
        .filter { it.url.isNotBlank() && heightOf(it) in 144..1080 && !it.qualityLabel.contains("HDR", ignoreCase = true) }
        .groupBy { heightOf(it) }
        .mapNotNull { (_, list) -> list.minByOrNull { codecRank(it) * 2 + if (it.mimeType.contains("mp4")) 0 else 1 } }
        .sortedByDescending { heightOf(it) }

/** Audio-saja terbaik (AAC/m4a bila ada, bitrate tertinggi). */
private fun pickAudio(dev: DevicePlayback?): String? {
    val a = dev?.audioOnly.orEmpty().filter { it.url.isNotBlank() }
    return (a.lastOrNull { it.mimeType.contains("mp4") } ?: a.lastOrNull())?.url
}

private const val PIP_TOGGLE_ACTION = "com.app.youtubego.PIP_TOGGLE"

/** Parameter PiP: rasio mengikuti video yang diputar (bukan selalu 16:9) + tombol putar/jeda di jendela mengambang. */
private fun buildPipParams(a: Activity, player: Player): PictureInPictureParams? {
    if (Build.VERSION.SDK_INT < 26) return null
    val vs = player.videoSize
    val ratio = if (vs.width > 0 && vs.height > 0) vs.width * vs.pixelWidthHeightRatio / vs.height else 16f / 9f
    val playing = player.isPlaying
    val label = if (playing) "Jeda" else "Putar"
    val intent = PendingIntent.getBroadcast(
        a, 0, Intent(PIP_TOGGLE_ACTION).setPackage(a.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val icon = android.graphics.drawable.Icon.createWithResource(a, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    val b = PictureInPictureParams.Builder()
        .setAspectRatio(Rational((ratio.coerceIn(0.5f, 2.3f) * 1000).toInt(), 1000))
        .setActions(listOf(RemoteAction(icon, label, label, intent)))
    if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(playing)
    return b.build()
}

private fun updatePipParams(a: Activity, player: Player) {
    if (Build.VERSION.SDK_INT < 26 || !a.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    runCatching { buildPipParams(a, player)?.let { a.setPictureInPictureParams(it) } }
}

/** Keluar dari layar pemutar: matikan masuk-PiP-otomatis agar layar lain tidak ikut mengecil jadi jendela PiP. */
private fun clearPipAutoEnter(a: Activity) {
    if (Build.VERSION.SDK_INT >= 31) runCatching { a.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()) }
}

private fun enterPip(a: Activity?, player: Player) {
    if (a == null || !a.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    runCatching {
        if (Build.VERSION.SDK_INT >= 26) a.enterPictureInPictureMode(buildPipParams(a, player) ?: PictureInPictureParams.Builder().build())
        else @Suppress("DEPRECATION") a.enterPictureInPictureMode()
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var c = this
    while (c is android.content.ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
private fun QueueRow(v: VideoSummary, current: Boolean, onPlay: () -> Unit, onRemove: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().background(if (current) YgSurfaceVariant else Color.Transparent).clickable(onClick = onPlay).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            v.thumbnailUrl, null, contentScale = ContentScale.Crop,
            modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(YgSurfaceVariant),
        )
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(v.author, color = YgMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Hapus dari antrean", tint = YgMuted) }
    }
}

private fun shareVideo(context: android.content.Context, title: String, videoId: String, startSec: Long?) {
    val url = "https://youtu.be/$videoId" + (startSec?.let { "?t=$it" } ?: "")
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "$title\n$url") }
    context.startActivity(Intent.createChooser(send, "Bagikan video"))
}
