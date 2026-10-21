package com.app.youtubego.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.common.formatDuration
import com.app.youtubego.data.local.DownloadStatus
import com.app.youtubego.presentation.theme.YgAccent
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

/** Urutan baris pada halaman "Anda" (Pengaturan dipisah di bawah garis). */
private val LibraryOrder = listOf(LibraryItem.HISTORY, LibraryItem.PLAYLISTS, LibraryItem.WATCH_LATER, LibraryItem.LIKED, LibraryItem.DOWNLOADS)

/**
 * Halaman "Anda": tanpa akun/login (semua data lokal di perangkat). Isinya judul, jalur "Terakhir ditonton"
 * untuk lanjut menonton cepat, lalu pintu masuk ke semua koleksi pribadi dengan ringkasan isi yang hidup.
 */
@Composable
fun LibraryScreen(onItemClick: (LibraryItem) -> Unit = {}, onVideoClick: (String) -> Unit = {}, vm: LibraryViewModel = hiltViewModel()) {
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
        item(key = "title") {
            Text("Anda", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp))
        }
        if (history.isNotEmpty()) {
            item(key = "recent-header") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Terakhir ditonton", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onItemClick(LibraryItem.HISTORY) }) { Text("Lihat semua", color = YgAccent) }
                }
            }
            item(key = "recent-row") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history.take(10), key = { it.id }) { v -> RecentCard(v, onVideoClick) }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = YgOutline)
            }
        }
        items(LibraryOrder, key = { it.name }) { LibraryRow(it, hint(it), onItemClick) }
        item(key = "divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = YgOutline) }
        item(key = "settings") { LibraryRow(LibraryItem.SETTINGS, hint(LibraryItem.SETTINGS), onItemClick) }
    }
}

@Composable
private fun RecentCard(video: VideoSummary, onClick: (String) -> Unit) {
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(12.dp)).clickable { onClick(video.id) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(YgSurfaceVariant)) {
            AsyncImage(model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(0.78f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Text(if (video.isLive) "LIVE" else formatDuration(video.lengthSeconds), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(video.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(video.author, color = YgMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, hint: String, onClick: (LibraryItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onClick(item) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
            Icon(item.icon, contentDescription = null, tint = YgAccent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(item.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(hint, color = YgMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = YgMuted)
    }
}
