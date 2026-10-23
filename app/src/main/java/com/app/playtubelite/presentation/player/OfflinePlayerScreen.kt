package com.app.playtubelite.presentation.player

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.app.playtubelite.data.local.DownloadStore
import com.app.playtubelite.data.local.UserLibraryStore
import com.app.playtubelite.playback.BackgroundPlaybackEffect
import com.app.playtubelite.playback.PlayerHolder
import com.app.playtubelite.domain.model.VideoSummary
import com.app.playtubelite.presentation.theme.ForceLightStatusBarIcons
import com.app.playtubelite.presentation.theme.PtMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OfflinePlayerViewModel @Inject constructor(
    private val downloads: DownloadStore,
    library: UserLibraryStore,
    val holder: PlayerHolder,
) : ViewModel() {
    val backgroundPlay = library.backgroundPlay
    fun fileUri(id: String): Uri? = downloads.fileFor(id).takeIf { it.exists() && downloads.isDone(id) }?.let { Uri.fromFile(it) }
    fun video(id: String): VideoSummary? = downloads.items.value.firstOrNull { it.video.id == id }?.video
}

/** Pemutar sederhana untuk video yang sudah diunduh: tanpa jaringan, dengan kontrol bawaan Media3. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun OfflinePlayerScreen(videoId: String, onBack: () -> Unit, vm: OfflinePlayerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uri = remember(videoId) { vm.fileUri(videoId) }
    val video = remember(videoId) { vm.video(videoId) }
    val holder = vm.holder
    val backgroundPlay by vm.backgroundPlay.collectAsState()
    val mediaId = "offline:$videoId"
    // Pemutar bersama + service: unduhan juga bisa diputar di latar belakang. Berkas lokal: tanpa jaringan.
    LaunchedEffect(uri) {
        if (uri != null && !holder.isCurrent(mediaId, uri.toString())) {
            holder.play(
                MediaItem.Builder().setMediaId(mediaId).setUri(uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(video?.title).setArtist(video?.author).build()).build(),
            )
        }
    }
    ForceLightStatusBarIcons()
    BackgroundPlaybackEffect(holder, backgroundPlay)
    DisposableEffect(mediaId) { onDispose { holder.stopIfCurrent(mediaId) } }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White) }
            Text(video?.title ?: "Unduhan", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (uri == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Berkas unduhan tidak ditemukan.", color = PtMuted)
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = true
                        this.player = holder.player
                    }
                },
                onRelease = { it.player = null },
            )
        }
    }
}
