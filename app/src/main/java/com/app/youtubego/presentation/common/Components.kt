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
import androidx.compose.ui.draw.drawBehind
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import com.app.youtubego.presentation.theme.YgAccent
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurfaceHigh
import com.app.youtubego.presentation.theme.YgSurfaceVariant
/** Shimmer tanpa library tambahan: brush linear bergerak, murah di GPU, aman untuk daftar panjang. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x = transition.animateFloat(-400f, 1200f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "x")
    // x dibaca di fase gambar (bukan saat komposisi): kerangka pemuatan bergerak tanpa menyusun ulang elemen tiap frame.
    val cVariant = YgSurfaceVariant
    val cHigh = YgSurfaceHigh
    return this.drawBehind {
        val xv = x.value
        drawRect(Brush.linearGradient(listOf(cVariant, cHigh, cVariant), start = Offset(xv, 0f), end = Offset(xv + 400f, 0f)))
    }
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
fun formatViews(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1f M".format(n / 1e9)
    n >= 1_000_000 -> "%.1f jt".format(n / 1e6)
    n >= 1_000 -> "%.1f rb".format(n / 1e3)
    else -> n.toString()
}
@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    // Keadaan error seragam dengan EmptyState: ikon, pesan, satu aksi jelas (tinggi menyesuaikan isi, aman untuk footer daftar).
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = YgAccent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(message, color = YgMuted, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        GradientButton("Coba lagi", onRetry)
    }
}

/** Unduh thumbnail daftar ke cache sebelum item masuk layar, sehingga saat digulir langsung tampil. */
@Composable
fun PrefetchThumbnails(videos: List<VideoSummary>) {
    val ctx = LocalContext.current
    // Hanya thumbnail BARU yang diantre (maks. 12 tiap kali daftar bertambah). Dulu seluruh daftar diantre ulang
    // setiap halaman baru dimuat: ratusan decode bersamaan di Beranda -> lonjakan memori -> aplikasi tertutup sendiri.
    val done = remember { HashSet<String>() }
    LaunchedEffect(videos.size) {
        val loader = coil.Coil.imageLoader(ctx)
        var queued = 0
        for (v in videos) {
            if (queued >= 12) break
            val u = v.thumbnailUrl
            if (u.isBlank() || !done.add(u)) continue
            queued++
            loader.enqueue(
                ImageRequest.Builder(ctx).data(u).size(400, 225)
                    .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.ENABLED).build()
            )
        }
    }
}
