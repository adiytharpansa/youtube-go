package com.app.youtubego.presentation.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.app.youtubego.domain.model.CaptionTrack
import com.app.youtubego.domain.model.CommentItem
import com.app.youtubego.domain.model.StreamOption
import com.app.youtubego.domain.model.VideoDetail
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.library.LibraryViewModel
import com.app.youtubego.presentation.library.PlaylistPickerDialog
import com.app.youtubego.presentation.library.VideoItem
import com.app.youtubego.data.local.DownloadStatus
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurfaceVariant
import kotlinx.coroutines.delay

private enum class PlayerSheet { SETTINGS, COMMENTS, DESCRIPTION }
private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

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
    val playlists by vm.playlists.collectAsState()
    val downloadItems by vm.downloadItems.collectAsState()
    var playlistDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val orientation = LocalConfiguration.current.orientation
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var adaptive by remember(videoId) { mutableStateOf(false) }
    var selectedHeight by remember(videoId) { mutableStateOf<Int?>(null) }
    var selectedStreamUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var captionIndex by remember(videoId) { mutableIntStateOf(-1) }

    val player = remember(videoId) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setMaxVideoSize(1280, 720).setMaxAudioBitrate(128_000)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            }
    }
    val snap = rememberPlayerSnapshot(player)

    DisposableEffect(lifecycleOwner, player) {
        val obs = LifecycleEventObserver { _, e ->
            // Dalam mode PiP video tetap berjalan; selain itu jeda saat layar ditinggalkan.
            if (e == Lifecycle.Event.ON_PAUSE && activity?.isInPictureInPictureMode != true) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            // Pulihkan orientasi & system bars bila layar ditinggalkan saat fullscreen.
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            player.release()
        }
    }
    // Picture-in-Picture: otomatis saat pengguna meninggalkan aplikasi sambil memutar.
    DisposableEffect(activity, player) {
        val a = activity as? ComponentActivity
        val leave = Runnable { if (player.isPlaying) enterPip(a) }
        val pipChanged = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        a?.addOnUserLeaveHintListener(leave)
        a?.addOnPictureInPictureModeChangedListener(pipChanged)
        onDispose { a?.removeOnUserLeaveHintListener(leave); a?.removeOnPictureInPictureModeChangedListener(pipChanged) }
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
    val captions = remember(detail) { detail?.captions.orEmpty().filter { it.url.isNotBlank() } }
    val nextVideo = detail?.recommendations?.firstOrNull { it.id != videoId }
    val ended = snap.state == Player.STATE_ENDED
    var autoCancelled by remember(videoId) { mutableStateOf(false) }
    var countdown by remember(videoId) { mutableIntStateOf(5) }
    LaunchedEffect(ended, autoplay, nextVideo?.id, autoCancelled) {
        if (ended && autoplay && nextVideo != null && !autoCancelled) {
            countdown = 5
            while (countdown > 0) { delay(1000); countdown-- }
            onVideoClick(nextVideo.id)
        }
    }

    // Siapkan media SEKALI per set URL segar (di luar cabang UI agar rotasi/layar penuh tidak mengulang putaran),
    // menunggu ekstraksi perangkat selesai lebih dulu. Urutan: DASH perangkat (adaptif), stream gabungan perangkat,
    // HLS perangkat, lalu cadangan server: gabungan, DASH, HLS. Takarir dari API ikut dipasang.
    LaunchedEffect(deviceSettled, detail != null, state is UiState.Loading, device?.dashUrl, device?.combined?.firstOrNull()?.url, device?.hlsUrl, detail?.combinedStreams?.firstOrNull()?.url, detail?.dashUrl, detail?.hlsUrl, highQuality) {
        val d = detail
        if (!deviceSettled || state is UiState.Loading) return@LaunchedEffect
        val devDash = device?.dashUrl
        val devCombined = (if (highQuality) device?.combined?.lastOrNull() else device?.combined?.firstOrNull())?.url
        val devHls = device?.hlsUrl
        val combined = (if (highQuality) d?.combinedStreams?.lastOrNull() else d?.combinedStreams?.firstOrNull())?.url
        val subs = d?.captions.orEmpty().filter { it.url.isNotBlank() }
        val item: MediaItem? = when {
            !devDash.isNullOrBlank() -> { adaptive = true; buildItem(devDash, MimeTypes.APPLICATION_MPD, subs) }
            !devCombined.isNullOrBlank() -> { adaptive = false; buildItem(devCombined, null, subs) }
            !devHls.isNullOrBlank() -> { adaptive = true; buildItem(devHls, MimeTypes.APPLICATION_M3U8, subs) }
            !combined.isNullOrBlank() -> { adaptive = false; buildItem(combined, null, subs) }
            !d?.dashUrl.isNullOrBlank() -> { adaptive = true; buildItem(d!!.dashUrl!!, MimeTypes.APPLICATION_MPD, subs) }
            !d?.hlsUrl.isNullOrBlank() -> { adaptive = true; buildItem(d!!.hlsUrl!!, MimeTypes.APPLICATION_M3U8, subs) }
            else -> null
        }
        if (item != null) { player.setMediaItem(item); player.prepare(); player.playWhenReady = true }
        else if (state !is UiState.Error) playerError = "Format putar untuk video ini sedang tidak tersedia dari jalur mana pun. Coba lagi nanti atau pilih video lain."
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
        if (h == null) b.setMaxVideoSize(1280, 720)
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
        player.setMediaItem(buildItem(o.url, null, captions), pos); player.prepare(); player.playWhenReady = true
    }
    fun applyCaption(i: Int) {
        captionIndex = i
        val b = player.trackSelectionParameters.buildUpon()
        if (i < 0) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(captions[i].languageCode)
        player.trackSelectionParameters = b.build()
    }
    val qualityChoices: List<Choice> = when {
        adaptive && adaptiveHeights.size > 1 -> listOf(Choice("Otomatis (maks 720p)", selectedHeight == null) { applyHeight(null) }) +
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
    val canPip = remember(activity) { activity?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true }

    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .then(if (!isFullscreen && !inPip) Modifier.statusBarsPadding() else Modifier),
    ) {
        Box(Modifier.fillMaxWidth().then(if (isFullscreen || inPip) Modifier.fillMaxHeight() else Modifier.aspectRatio(16f / 9f))) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player; useController = false; keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!inPip) {
                YtPlayerOverlay(
                    player = player, snap = snap, title = detail?.summary?.title.orEmpty(), isFullscreen = isFullscreen,
                    hasCaptions = captions.isNotEmpty(), captionsOn = captionIndex >= 0, canPip = canPip,
                    onToggleCaptions = { applyCaption(if (captionIndex < 0) 0 else -1) },
                    onOpenSettings = { sheet = PlayerSheet.SETTINGS },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onBack = { if (isFullscreen) isFullscreen = false else onBack() },
                    onEnterPip = { enterPip(activity) },
                )
            }
            playerError?.let { msg ->
                Column(
                    Modifier.fillMaxSize().background(Color.Black.copy(0.75f)).padding(16.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(msg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { playerError = null; vm.retry() }) { Text("Coba lagi") }
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
                    is UiState.Loading -> Column { VideoCardShimmer() }
                    is UiState.Error -> ErrorBox(s.message) { vm.retry() }
                    is UiState.Success -> {
                        val d = s.data
                        val liked = videoId in likedIds
                        val saved = videoId in savedIds
                        val subscribed = d.summary.authorId in subscribedIds
                        LazyColumn(Modifier.fillMaxSize()) {
                            item { TitleBlock(d) { sheet = PlayerSheet.DESCRIPTION } }
                            item { ChannelRow(d, subscribed, onOpen = { onChannelClick(d.summary.authorId) }) { vm.toggleSubscribe(d.summary.authorId) } }
                            item {
                                val downloadStatus = downloadItems.firstOrNull { it.video.id == videoId }?.status
                                ActionRow(
                                    downloadStatus = downloadStatus, onDownload = vm::download, onPlaylist = { playlistDialog = true },
                                    likeLabel = if (d.likeCount + (if (liked) 1 else 0) > 0) formatViewsId(d.likeCount + (if (liked) 1 else 0)) else "Suka",
                                    liked = liked, saved = saved,
                                    onLike = vm::toggleLike, onSave = vm::toggleSave,
                                    onShare = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${d.summary.title}\nhttps://youtu.be/$videoId")
                                        }
                                        context.startActivity(Intent.createChooser(send, "Bagikan video"))
                                    },
                                )
                            }
                            item { CommentsTeaser(comments) { sheet = PlayerSheet.COMMENTS } }
                            item {
                                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Video berikutnya", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text("Putar otomatis", color = YgMuted, style = MaterialTheme.typography.labelMedium)
                                    Switch(checked = autoplay, onCheckedChange = vm::setAutoplay, modifier = Modifier.padding(start = 8.dp),
                                        colors = SwitchDefaults.colors(checkedTrackColor = YtRed))
                                }
                            }
                            items(d.recommendations, key = { it.id }) { v -> VideoItem(v, onVideoClick, onChannelClick, libVm) }
                        }
                        when (sheet) {
                            PlayerSheet.COMMENTS -> CommentsSheet(comments) { sheet = null }
                            PlayerSheet.DESCRIPTION -> DescriptionSheet(d) { sheet = null }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
    if (playlistDialog) {
        PlaylistPickerDialog(
            playlists = playlists, videoId = videoId,
            onToggle = { vm.togglePlaylist(it.id) }, onCreate = vm::createPlaylist, onDismiss = { playlistDialog = false },
        )
    }
    if (sheet == PlayerSheet.SETTINGS) {
        PlayerSettingsSheet(qualityLabel, qualityChoices, speedLabel, speedChoices, captionLabel, captionChoices) { sheet = null }
    }
}

@Composable
private fun TitleBlock(d: VideoDetail, onMore: () -> Unit) {
    val meta = listOfNotNull(
        "${formatViewsId(d.summary.viewCount)} x ditonton",
        formatRelativeTime(d.summary.published).takeIf { it.isNotEmpty() },
    ).joinToString(" • ")
    Column(Modifier.fillMaxWidth().clickable(onClick = onMore).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(d.summary.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("$meta  ...selengkapnya", color = YgMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ChannelRow(d: VideoDetail, subscribed: Boolean, onOpen: () -> Unit, onSubscribe: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(d.summary.authorAvatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape).background(YgSurfaceVariant).clickable(enabled = d.summary.authorId.isNotBlank(), onClick = onOpen))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp).clickable(enabled = d.summary.authorId.isNotBlank(), onClick = onOpen)) {
            Text(d.summary.author, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.subscriberText.isNotBlank()) Text(d.subscriberText, color = YgMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        if (d.summary.authorId.isNotBlank()) {
            if (subscribed) FilledTonalButton(onClick = onSubscribe) {
                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(18.dp)); Text(" Terlangganan")
            } else Button(onClick = onSubscribe, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("Berlangganan") }
        }
    }
}

@Composable
private fun ActionRow(
    downloadStatus: DownloadStatus?, onDownload: () -> Unit, onPlaylist: () -> Unit,
    likeLabel: String, liked: Boolean, saved: Boolean, onLike: () -> Unit, onSave: () -> Unit, onShare: () -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ActionPill(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, likeLabel, liked, onLike) }
        item { ActionPill(Icons.Filled.Share, "Bagikan", false, onShare) }
        item { ActionPill(if (saved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, if (saved) "Tersimpan" else "Simpan", saved, onSave) }
        item { ActionPill(Icons.AutoMirrored.Filled.PlaylistAdd, "Playlist", false, onPlaylist) }
        item {
            val label = when (downloadStatus) {
                DownloadStatus.DONE -> "Diunduh"
                DownloadStatus.DOWNLOADING -> "Mengunduh..."
                DownloadStatus.PAUSED, DownloadStatus.FAILED -> "Lanjutkan unduh"
                null -> "Unduh"
            }
            ActionPill(if (downloadStatus == DownloadStatus.DONE) Icons.Filled.DownloadDone else Icons.Filled.Download, label, downloadStatus == DownloadStatus.DONE) {
                if (downloadStatus != DownloadStatus.DONE && downloadStatus != DownloadStatus.DOWNLOADING) onDownload()
            }
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(YgSurfaceVariant).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) YtRed else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Text(" $label", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CommentsTeaser(comments: UiState<List<CommentItem>>, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(MaterialTheme.shapes.medium)
            .background(YgSurfaceVariant).clickable(onClick = onOpen).padding(12.dp),
    ) {
        Text("Komentar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        when (comments) {
            is UiState.Loading -> Text("Memuat komentar...", color = YgMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            is UiState.Error -> Text("Komentar tidak tersedia saat ini.", color = YgMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            is UiState.Success -> {
                val top = comments.data.firstOrNull()
                if (top == null) Text("Belum ada komentar.", color = YgMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                else Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(top.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.DarkGray))
                    Text(top.content, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

private fun speedText(s: Float): String = if (s == 1f) "Normal" else "${s}x".replace(".0x", "x")

private fun buildItem(uri: String, mime: String?, subs: List<CaptionTrack>): MediaItem {
    val b = MediaItem.Builder().setUri(uri)
    if (mime != null) b.setMimeType(mime)
    if (subs.isNotEmpty()) b.setSubtitleConfigurations(subs.map {
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url)).setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(it.languageCode).setLabel(it.label.ifBlank { it.languageCode }).setSelectionFlags(0).build()
    })
    return b.build()
}

private fun enterPip(a: Activity?) {
    if (a == null || !a.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    runCatching {
        if (Build.VERSION.SDK_INT >= 26) a.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        else @Suppress("DEPRECATION") a.enterPictureInPictureMode()
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var c = this
    while (c is android.content.ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}
