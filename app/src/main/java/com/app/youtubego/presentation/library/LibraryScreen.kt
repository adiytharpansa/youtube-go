package com.app.youtubego.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgAccent2
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgOutline
import com.app.youtubego.presentation.theme.YgSurfaceVariant

enum class LibraryItem(val label: String, val hint: String, val icon: ImageVector) {
    HISTORY("Riwayat", "Video yang baru kamu tonton", Icons.Filled.History),
    WATCH_LATER("Tonton nanti", "Belum ada video tersimpan", Icons.Filled.WatchLater),
    LIKED("Video disukai", "Belum ada video disukai", Icons.Filled.ThumbUp),
    DOWNLOADS("Unduhan", "Tonton tanpa internet", Icons.Filled.Download),
    PLAYLISTS("Playlist", "Kumpulkan video favoritmu", Icons.AutoMirrored.Filled.PlaylistPlay),
    SETTINGS("Pengaturan", "Kualitas, hemat data, tema", Icons.Filled.Settings),
}

/** Halaman "Anda": akun lokal + pintu masuk ke semua koleksi pribadi. */
@Composable
fun LibraryScreen(onItemClick: (LibraryItem) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(YgAccent, YgAccent2))),
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = Color.White, style = MaterialTheme.typography.titleLarge) }
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Akun lokal", style = MaterialTheme.typography.titleLarge)
                    Text("Datamu tersimpan di perangkat ini", color = YgMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(LibraryItem.entries.take(5), key = { it.name }) { LibraryRow(it, onItemClick) }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = YgOutline) }
        item { LibraryRow(LibraryItem.SETTINGS, onItemClick) }
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, onClick: (LibraryItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onClick(item) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
            Icon(item.icon, contentDescription = null, tint = YgAccent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(item.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(item.hint, color = YgMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = YgMuted)
    }
}

