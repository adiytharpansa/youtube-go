package com.app.youtubego.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.youtubego.presentation.theme.*

enum class LibraryItem(val key: String, val label: String, val hint: String, val icon: ImageVector) {
    HISTORY("history", "Riwayat", "Video yang baru kamu tonton", Icons.Filled.History),
    WATCH_LATER("saved", "Tonton nanti", "Video yang kamu simpan", Icons.Filled.WatchLater),
    LIKED("liked", "Video disukai", "Video yang kamu sukai", Icons.Filled.ThumbUp),
    DOWNLOADS("downloads", "Unduhan", "Tonton tanpa internet", Icons.Filled.Download),
    PLAYLISTS("playlists", "Playlist", "Kumpulkan video favoritmu", Icons.AutoMirrored.Filled.PlaylistPlay),
    SETTINGS("settings", "Pengaturan", "Preferensi aplikasi", Icons.Filled.Settings),
}

@Composable
fun LibraryScreen(
    onItemClick: (LibraryItem) -> Unit = {},
    vm: LibraryHomeViewModel = hiltViewModel(),
) {
    val history by vm.history.collectAsState()
    val saved by vm.saved.collectAsState()
    val liked by vm.liked.collectAsState()
    val subscribed by vm.subscribed.collectAsState()
    val autoplay by vm.autoplay.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().background(YgBackground).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 22.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Koleksimu", style = MaterialTheme.typography.headlineSmall, color = YgOnSurface)
                    Text("Semua yang kamu simpan, di satu tempat", color = YgMuted, style = MaterialTheme.typography.bodySmall)
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(YgSurfaceVariant).border(1.dp, YgOutline, CircleShape).clickable { onItemClick(LibraryItem.SETTINGS) },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Settings, contentDescription = "Pengaturan", tint = YgMuted, modifier = Modifier.size(20.dp)) }
            }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF24151A), YgSurfaceElevated, Color(0xFF18191E))))
                    .border(1.dp, YgOutlineStrong.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                    .clickable { onItemClick(LibraryItem.SETTINGS) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(YgGradientAccent).padding(2.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(YgSurface), contentAlignment = Alignment.Center) {
                        Text("A", color = YgOnSurface, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text("Akun lokal", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(3.dp))
                    Text("Datamu tersimpan di perangkat ini", color = YgMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.clip(CircleShape).background(YgAccent.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Shield, null, tint = YgAccent2, modifier = Modifier.size(13.dp))
                        Text("Privat & aman", color = YgAccent2, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = YgMuted)
            }
            Spacer(Modifier.height(20.dp))
        }
        item { YgSectionHeader("Perpustakaan", "Lanjutkan dari aktivitasmu") }
        item { LibraryTile(LibraryItem.HISTORY, "${history.size} video", onItemClick) }
        item { LibraryTile(LibraryItem.WATCH_LATER, "${saved.size} video", onItemClick) }
        item { LibraryTile(LibraryItem.LIKED, "${liked.size} video", onItemClick) }
        item { LibraryTile(LibraryItem.DOWNLOADS, "Belum tersedia", onItemClick) }
        item { LibraryTile(LibraryItem.PLAYLISTS, "Belum tersedia", onItemClick) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(YgDivider))
                Text("  PREFERENSI  ", color = YgMutedSoft, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp))
                Box(Modifier.weight(1f).height(1.dp).background(YgDivider))
            }
        }
        item { LibraryTile(LibraryItem.SETTINGS, "Putar otomatis: ${if (autoplay) "aktif" else "nonaktif"}", onItemClick) }
        item {
            Text(
                "Mengikuti ${subscribed.size} channel",
                color = YgMutedSoft,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun LibraryTile(item: LibraryItem, hint: String, onClick: (LibraryItem) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(YgSurfaceElevated)
            .border(1.dp, YgOutline.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .clickable { onClick(item) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
            Icon(item.icon, contentDescription = null, tint = YgAccent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(item.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(hint, color = YgMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = YgMutedSoft)
    }
}
