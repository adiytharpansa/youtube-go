package com.app.youtubego.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.youtubego.presentation.theme.*

/** Tombol utama bergradasi aksen (#FF2E4D -> #FF6A3D), tinggi 48dp, shadow lembut. */
@Composable
fun GradientButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(YgGradientAccent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(YgSurfaceVariant)
            .border(1.dp, YgOutline, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = YgOnSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Keadaan kosong / offline / error: ikon gradien, judul tebal, deskripsi, aksi jelas. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ikon dengan lingkaran gradien + glow
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(YgSurfaceVariant)
                    .border(1.dp, YgOutline, CircleShape)
            )
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Brush.radialGradient(listOf(YgAccent.copy(alpha = 0.22f), Color.Transparent))),
            )
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(YgGradientAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, color = YgMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
        if (actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            GradientButton(actionLabel, onAction)
        }
    }
}
