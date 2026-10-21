package com.app.youtubego.presentation.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgOnSurface
import com.app.youtubego.presentation.theme.YgOutline
import com.app.youtubego.presentation.theme.pressScaleOf

/**
 * Chip filter bergaya seragam di seluruh aplikasi: kontur tipis, terpilih = rona aksen lembut + teks tebal
 * berwarna kontras tinggi (YgOnSurface), dan mengecil sedikit saat ditekan.
 */
@Composable
fun YgChip(label: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScaleOf(source, 0.95f)
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = leading, interactionSource = source,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = YgAccent.copy(alpha = 0.20f),
            labelColor = YgMuted, selectedLabelColor = YgOnSurface,
            iconColor = YgMuted, selectedLeadingIconColor = YgAccent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = YgOutline, selectedBorderColor = YgAccent.copy(alpha = 0.55f),
        ),
    )
}
