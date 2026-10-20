package com.app.youtubego.presentation.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurfaceVariant

/** Shimmer tanpa library tambahan: brush linear bergerak, murah di GPU, aman untuk daftar panjang. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(-400f, 1200f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val brush = Brush.linearGradient(listOf(YgSurfaceVariant, Color(0xFF2A2D35), YgSurfaceVariant), start = Offset(x, 0f), end = Offset(x + 400f, 0f))
    return this.background(brush)
}

@Composable
fun VideoCardShimmer() {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).ygShimmer())
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).ygShimmer())
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).ygShimmer())
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth(0.55f).height(11.dp).clip(MaterialTheme.shapes.extraSmall).ygShimmer())
            }
        }
    }
}

@Composable
fun VideoCard(video: VideoSummary, onClick: (String) -> Unit) {
    // Legacy wrapper — arahkan ke kartu Pro yang lebih menarik
    VideoCardPro(video = video, onClick = onClick)
}

fun formatDuration(sec: Long): String {
    if (sec <= 0) return "0:00"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatViews(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1f M".format(n / 1e9)
    n >= 1_000_000 -> "%.1f jt".format(n / 1e6)
    n >= 1_000 -> "%.1f rb".format(n / 1e3)
    else -> n.toString()
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(YgSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("!", color = YgMuted, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(12.dp))
        Text(message, color = YgMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        GradientButton(label = "Coba lagi", onClick = onRetry)
    }
}
