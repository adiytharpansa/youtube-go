package com.app.youtubego.presentation.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.theme.*

data class VideoAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** "2 hari lalu" dari epoch detik (format `published` Invidious). */
fun formatRelativeTime(epochSec: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (epochSec <= 0) return ""
    val d = (nowMs / 1000 - epochSec).coerceAtLeast(0)
    return when {
        d < 60 -> "baru saja"
        d < 3_600 -> "${d / 60} menit lalu"
        d < 86_400 -> "${d / 3_600} jam lalu"
        d < 604_800 -> "${d / 86_400} hari lalu"
        d < 2_592_000 -> "${d / 604_800} minggu lalu"
        d < 31_536_000 -> "${d / 2_592_000} bulan lalu"
        else -> "${d / 31_536_000} tahun lalu"
    }
}

/** 1,2 jt / 3,4 rb dengan koma desimal Indonesia; ",0" dibuang. */
fun formatViewsId(n: Long): String {
    fun f(v: Double, unit: String) = String.format(java.util.Locale("id", "ID"), "%.1f", v).removeSuffix(",0") + " $unit"
    return when {
        n >= 1_000_000_000 -> f(n / 1e9, "M")
        n >= 1_000_000 -> f(n / 1e6, "jt")
        n >= 1_000 -> f(n / 1e3, "rb")
        else -> n.toString()
    }
}

/**
 * Kartu video premium:
 * - thumbnail 16:9 sudut 16dp + border halus + shadow lembut
 * - badge durasi / LIVE dengan animasi titik berkedip
 * - bar bawah avatar + judul + meta Channel • views • waktu
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCardPro(
    video: VideoSummary,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onChannelClick: (String) -> Unit = {},
    actions: List<VideoAction> = emptyList(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    val meta = listOfNotNull(
        video.author.takeIf { it.isNotBlank() },
        if (video.isLive) "Sedang live" else "${formatViewsId(video.viewCount)} ditonton",
        formatRelativeTime(video.published).takeIf { it.isNotEmpty() && !video.isLive },
    ).joinToString(" • ")

    Column(
        modifier
            .fillMaxWidth()
            .clickable { onClick(video.id) }
            .padding(bottom = 8.dp)
    ) {
        // ── Thumbnail ──────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(YgSurfaceVariant)
                .border(0.8.dp, YgOutline.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(video.thumbnailUrl)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Gradient bawah untuk keterbacaan badge
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.35f)))
                    )
            )
            // Badge durasi / LIVE
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (video.isLive) {
                    LivePill()
                } else {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(0.84f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            formatDuration(video.lengthSeconds),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            // Tombol tonton nanti (visual saja — aksi via sheet)
            if (actions.isNotEmpty()) {
                // placeholder subtle hover area — actual action via bottom sheet
            }
        }

        // ── Info bawah ────────────────────────────
        Row(
            Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar dengan ring gradient
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(YgGradientAccent)
                    .padding(1.5.dp)
            ) {
                AsyncImage(
                    model = video.authorAvatarUrl,
                    contentDescription = "Buka channel ${video.author}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(YgSurfaceVariant)
                        .clickable(enabled = video.authorId.isNotBlank()) { onChannelClick(video.authorId) },
                )
            }

            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = MaterialTheme.typography.titleMedium.lineHeight),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = YgOnSurface
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        meta,
                        color = YgMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            if (actions.isNotEmpty()) {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opsi lainnya", tint = YgMuted, modifier = Modifier.size(18.dp))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }

    if (menuOpen) {
        ModalBottomSheet(
            onDismissRequest = { menuOpen = false },
            containerColor = YgSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            // Handle bar
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(YgOutlineStrong)
            )
            Spacer(Modifier.height(8.dp))
            actions.forEach { a ->
                ListItem(
                    headlineContent = { Text(a.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)) },
                    leadingContent = {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(a.icon, contentDescription = null, tint = YgOnSurface, modifier = Modifier.size(18.dp))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { menuOpen = false; a.onClick() },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LivePill() {
    val infinite = rememberInfiniteTransition(label = "livePulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(YgLive)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = alpha)))
        Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp))
    }
}
