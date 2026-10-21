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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.youtubego.data.local.DownloadStatus
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgAccent2
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgOutline
import com.app.youtubego.presentation.theme.YgSurfaceVariant

enum class LibraryItem(val label: String, val icon: ImageVector) {
    HISTORY("Riwayat", Icons.Filled.History),
    WATCH_LATER("Tonton nanti", Icons.Filled.WatchLater),
    LIKED("Video disukai", Icons.Filled.ThumbUp),
    DOWNLOADS("Unduhan", Icons.Filled.Download),
    PLAYLISTS("Playlist", Icons.AutoMirrored.Filled.PlaylistPlay),
    SETTINGS("Pengaturan", Icons.Filled.Settings),
}

/** Halaman "Anda": akun lokal + pintu masuk ke semua koleksi pribadi, dengan ringkasan isi yang hidup. */
@Composable
fun LibraryScreen(onItemClick: (LibraryItem) -> Unit = {}, vm: LibraryViewModel = hiltViewModel()) {
    val history by vm.historyVideos.collectAsState()
    val saved by vm.saved.collectAsState()
    val liked by vm.liked.collectAsState()
    val downloads by vm.downloadItems.collectAsState()
    val playlists by vm.playlists.collectAsState()

    fun hint(item: LibraryItem): String = when (item) {
        LibraryItem.HISTORY -> if (history.isEmpty()) "Video yang baru kamu tonton" else "${history.size} video ditonton"
        LibraryItem.WATCH_LATER -> if (saved.isEmpty()) "Belum ada video tersimpan" else "${saved.size} video tersimpan"
        LibraryItem.LIKED -> if (liked.isEmpty()) "Belum ada video disukai" else "${liked.size} video disukai"
        LibraryItem.DOWNLOADS -> downloads.count { it.status == DownloadStatus.DONE }.let { if (it == 0) "Tonton tanpa internet" else "$it video siap offline" }
        LibraryItem.PLAYLISTS -> if (playlists.isEmpty()) "Kumpulkan video favoritmu" else "${playlists.size} playlist"
        LibraryItem.SETTINGS -> "Kualitas, hemat data, putar otomatis"
    }

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
        items(LibraryItem.entries.take(5), key = { it.name }) { LibraryRow(it, hint(it), onItemClick) }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = YgOutline) }
        item { LibraryRow(LibraryItem.SETTINGS, hint(LibraryItem.SETTINGS), onItemClick) }
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, hint: String, onClick: (LibraryItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onClick(item) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
            Icon(item.icon, contentDescription = null, tint = YgAccent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(item.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(hint, color = YgMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = YgMuted)
    }
}
