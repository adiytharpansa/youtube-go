package com.app.youtubego.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurface
import com.app.youtubego.presentation.theme.YgSurfaceVariant
import java.util.Locale

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
    fun f(v: Double, unit: String) = String.format(Locale("id", "ID"), "%.1f", v).removeSuffix(",0") + " $unit"
    return when {
        n >= 1_000_000_000 -> f(n / 1e9, "M")
        n >= 1_000_000 -> f(n / 1e6, "jt")
        n >= 1_000 -> f(n / 1e3, "rb")
        else -> n.toString()
    }
}

/**
 * Kartu video versi profesional:
 * - meta rapi: Channel • 1,2 jt tontonan • 2 hari lalu
 * - avatar bisa diklik ke channel, badge LIVE berwarna aksen
 * - menu titik tiga (bottom sheet) untuk simpan / bagikan / unduh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCardPro(
    video: VideoSummary,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onChannelClick: (String) -> Unit = {},
    actions: List<VideoAction> = emptyList(),
    /** 0..1 = bagian video yang sudah ditonton (bilah merah di dasar thumbnail); 0 = tidak tampil. */
    watchedFraction: Float = 0f,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Umpan balik sentuh tanpa ripple: kartu mengecil sedikit saat ditekan, kembali dengan pegas singkat.
    val pressSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.975f else 1f, androidx.compose.animation.core.tween(if (pressed) 70 else 140), label = "press",
    )
    // Statistik: tontonan + waktu unggah (atau "Sedang live").
    val stats = listOfNotNull(
        if (video.isLive) "Sedang live" else "${formatViewsId(video.viewCount)} tontonan",
        formatRelativeTime(video.published).takeIf { it.isNotEmpty() && !video.isLive },
    ).joinToString(" • ")

    Column(
        modifier.fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clickable(interactionSource = pressSource, indication = null, onClickLabel = "Putar ${video.title}", role = Role.Button) { onClick(video.id) }
            .padding(bottom = 8.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium).background(YgSurfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(video.thumbnailUrl)
                    .crossfade(true).diskCachePolicy(CachePolicy.ENABLED).memoryCachePolicy(CachePolicy.ENABLED).build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            if (watchedFraction > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(0.35f))) {
                Box(Modifier.fillMaxWidth(watchedFraction.coerceIn(0f, 1f)).fillMaxHeight().background(Color(0xFFFF0000)))
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(if (video.isLive) YgAccent else Color.Black.copy(0.78f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(if (video.isLive) "LIVE" else formatDuration(video.lengthSeconds), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        // Skala jarak 4/8/12/16: avatar sejajar tepi thumbnail (12dp); area sentuh avatar 48dp.
        Row(Modifier.padding(start = 7.dp, end = 4.dp, top = 7.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .clickable(enabled = video.authorId.isNotBlank(), onClickLabel = "Buka channel ${video.author}", role = Role.Button) { onChannelClick(video.authorId) },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = video.authorAvatarUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(YgSurfaceVariant),
                )
            }
            Column(Modifier.weight(1f).padding(start = 7.dp, top = 5.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                // Satu baris "Channel • statistik": kartu lebih pendek, daftar lebih padat.
                Text(listOfNotNull(video.author.takeIf { it.isNotBlank() }, stats).joinToString(" \u2022 "),
                    color = YgMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (actions.isNotEmpty()) {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Opsi lainnya", tint = YgMuted) }
            }
        }
    }

    if (menuOpen) {
        ModalBottomSheet(onDismissRequest = { menuOpen = false }, containerColor = YgSurface) {
            actions.forEach { a ->
                ListItem(
                    headlineContent = { Text(a.label) },
                    leadingContent = { Icon(a.icon, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { menuOpen = false; a.onClick() },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
