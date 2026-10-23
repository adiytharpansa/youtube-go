package com.app.playtubelite.presentation.common

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
import com.app.playtubelite.presentation.theme.PtAccent
import com.app.playtubelite.presentation.theme.PtMuted
import com.app.playtubelite.presentation.theme.PtOnSurface
import com.app.playtubelite.presentation.theme.PtOutline
import com.app.playtubelite.presentation.theme.pressScaleOf

/**
 * Chip filter bergaya seragam di seluruh aplikasi: kontur tipis, terpilih = rona aksen lembut + teks tebal
 * berwarna kontras tinggi (PtOnSurface), dan mengecil sedikit saat ditekan.
 */
@Composable
fun PtChip(label: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScaleOf(source, 0.95f)
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = leading, interactionSource = source,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = PtAccent.copy(alpha = 0.20f),
            labelColor = PtMuted, selectedLabelColor = PtOnSurface,
            iconColor = PtMuted, selectedLeadingIconColor = PtAccent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = PtOutline, selectedBorderColor = PtAccent.copy(alpha = 0.55f),
        ),
    )
}
