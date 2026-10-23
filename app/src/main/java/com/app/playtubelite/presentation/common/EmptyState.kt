package com.app.playtubelite.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.playtubelite.presentation.theme.PtAccent
import com.app.playtubelite.presentation.theme.PtAccent2
import com.app.playtubelite.presentation.theme.PtMuted
import com.app.playtubelite.presentation.theme.PtOutline
import com.app.playtubelite.presentation.theme.PtSurfaceVariant

/**
 * Tombol utama bergradasi aksen (#FF2E4D -> #FF6A3D), area sentuh min. 48dp.
 * Ada bayangan berwarna aksen yang lembut dan getar halus saat disentuh (skala tekan datang dari PressIndication global).
 */
@Composable
fun GradientButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .heightIn(min = 48.dp)
            .shadow(8.dp, shape, ambientColor = PtAccent.copy(alpha = 0.35f), spotColor = PtAccent.copy(alpha = 0.55f))
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(PtAccent, PtAccent2)))
            .clickable(role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}

/**
 * Satu komponen untuk keadaan kosong / offline / error: ikon, judul, penjelasan, satu aksi jelas.
 * Muncul dengan pudar + geser naik halus. [extra] (opsional) tampil di bawah aksi dan boleh selebar layar
 * (mis. baris saran channel); isinya ikut bergulir bila layar pendek.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(260, easing = FastOutSlowInEasing), label = "emptyIn")

    // BoxWithConstraints: isi bergulir bila layar pendek, tetapi tetap di tengah vertikal bila muat (heightIn min = tinggi layar).
    BoxWithConstraints(modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(vertical = 24.dp)
                .graphicsLayer { alpha = progress; translationY = (1f - progress) * 12.dp.toPx() },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Lingkaran ikon: cahaya aksen lembut di belakang + garis tepi tipis supaya tidak menyatu dengan latar.
            Box(
                Modifier
                    .size(128.dp)
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(listOf(PtAccent.copy(alpha = 0.20f), Color.Transparent)),
                            radius = size.minDimension / 2f,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(PtSurfaceVariant).border(1.dp, PtOutline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = PtAccent, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    message, color = PtMuted, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                if (actionLabel != null) {
                    Spacer(Modifier.height(24.dp))
                    GradientButton(actionLabel, onAction)
                }
            }
            extra?.invoke(this)
        }
    }
}
