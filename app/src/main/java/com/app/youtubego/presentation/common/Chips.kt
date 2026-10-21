package com.app.youtubego.presentation.common

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgOutline

/** Chip filter bergaya seragam di seluruh aplikasi: kontur tipis, terpilih = rona aksen merah lembut. */
@Composable
fun YgChip(label: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = leading,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = YgAccent.copy(alpha = 0.18f),
            labelColor = YgMuted, selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = YgMuted, selectedLeadingIconColor = YgAccent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = YgOutline, selectedBorderColor = YgAccent.copy(alpha = 0.55f),
        ),
    )
}
