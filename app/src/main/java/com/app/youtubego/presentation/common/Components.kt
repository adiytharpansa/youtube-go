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
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).shimmer())
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).shimmer())
            Column(Modifier.padding(start = 12.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
                Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
            }
        }
    }
}
@Composable
fun VideoCard(video: VideoSummary, onClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick(video.id) }.padding(bottom = 10.dp)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).background(YgSurfaceVariant)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(video.thumbnailUrl)
                    .crossfade(true).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(MaterialTheme.shapes.extraSmall).background(Color.Black.copy(0.78f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(if (video.isLive) "LIVE" else formatDuration(video.lengthSeconds), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            AsyncImage(model = video.authorAvatarUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(38.dp).clip(CircleShape).background(YgSurfaceVariant))
            Column(Modifier.padding(start = 12.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${video.author} - ${formatViews(video.viewCount)} x ditonton", color = YgMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
fun formatDuration(sec: Long): String { if (sec <= 0) return "0:00"; val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60; return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s) }
fun formatViews(n: Long): String = when { n >= 1_000_000_000 -> "%.1f M".format(n / 1e9); n >= 1_000_000 -> "%.1f jt".format(n / 1e6); n >= 1_000 -> "%.1f rb".format(n / 1e3); else n.toString() }
@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = YgMuted); Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(onClick = onRetry) { Text("Coba lagi") }
    }
}
