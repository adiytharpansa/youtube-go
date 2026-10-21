package com.app.youtubego.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Judul layar tab utama (Langganan, Anda): SATU gaya (titleLarge) supaya semua tab seragam,
 * dengan slot aksi opsional di kanan (mis. ikon Pengaturan).
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    horizontal: Dp = 16.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = horizontal, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}
