package com.app.youtubego.presentation.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.youtubego.presentation.theme.*

// ─────────────────────────────────────────────
// Latar belakang gradien halus untuk scaffold
// ─────────────────────────────────────────────
@Composable
fun YgScaffoldBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .background(YgBackground)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF15151A).copy(alpha = 0.95f),
                        Color.Transparent,
                        Color.Transparent
                    )
                )
            )
    ) { content() }
}

// ─────────────────────────────────────────────
// Chip kategori premium — gradient saat terpilih
// ─────────────────────────────────────────────
@Composable
fun YgChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    if (selected) {
        Box(
            Modifier
                .height(36.dp)
                .clip(shape)
                .background(YgGradientAccent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                leadingIcon?.invoke()
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    } else {
        Box(
            Modifier
                .height(36.dp)
                .clip(shape)
                .background(YgSurfaceVariant)
                .border(1.dp, YgOutline, shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                leadingIcon?.let { Box(Modifier.size(16.dp)) { it() } }
                Text(label, color = YgOnSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

// ─────────────────────────────────────────────
// Judul section dengan garis aksen
// ─────────────────────────────────────────────
@Composable
fun YgSectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(YgGradientAccent)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            if (subtitle != null) Text(subtitle, color = YgMuted, style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

// ─────────────────────────────────────────────
// Shimmer premium
// ─────────────────────────────────────────────
@Composable
fun Modifier.ygShimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "ygShimmer")
    val x by transition.animateFloat(
        initialValue = -500f, targetValue = 1300f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        listOf(YgSurfaceVariant, YgSurfaceHigher, YgSurfaceVariant),
        start = Offset(x - 400f, 0f), end = Offset(x, 0f)
    )
    return this.background(brush)
}

// Pill kecil untuk LIVE / durasi
@Composable
fun YgPill(
    text: String,
    isLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isLive) YgLive else Color.Black.copy(0.82f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isLive) Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
            Text(
                text, color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
            )
        }
    }
}

// Tombol aksi bulat kecil di atas thumbnail (tonton nanti, antrean)
@Composable
fun YgIconPill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
