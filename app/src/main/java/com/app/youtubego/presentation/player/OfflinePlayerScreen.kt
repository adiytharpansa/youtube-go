package com.app.youtubego.presentation.player

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.app.youtubego.data.local.DownloadStore
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.theme.YgMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OfflinePlayerViewModel @Inject constructor(private val downloads: DownloadStore) : ViewModel() {
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val exo = remember(videoId) {
        ExoPlayer.Builder(context).build().apply {
            if (uri != null) { setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true }
        }
    }
    DisposableEffect(exo, lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) exo.pause() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); exo.release() }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White) }
            Text(video?.title ?: "Unduhan", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (uri == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Berkas unduhan tidak ditemukan.", color = YgMuted)
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = true
                        this.player = exo
                    }
                },
            )
        }
    }
}
