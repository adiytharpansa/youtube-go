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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.youtubego.presentation.theme.*

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
                    Modifier.size(42.dp).clip(CircleShape).background(YgSurfaceVariant).border(1.dp, YgOutline, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Settings, contentDescription = "Pengaturan", tint = YgMuted, modifier = Modifier.size(20.dp)) }
            }
        }
        item {
            // Profil lokal — gaya premium dengan aksen glow
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF24151A), YgSurfaceElevated, Color(0xFF18191E))))
                    .border(1.dp, YgOutlineStrong.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                    .clickable { onItemClick(LibraryItem.SETTINGS) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(YgGradientAccent).padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
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
        items(LibraryItem.entries.take(5), key = { it.name }) { LibraryTile(it, onItemClick) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(YgDivider))
                Text("  PREFERENSI  ", color = YgMutedSoft, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp))
                Box(Modifier.weight(1f).height(1.dp).background(YgDivider))
            }
        }
        item { LibraryTile(LibraryItem.SETTINGS, onItemClick) }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.PlayCircle, null, tint = YgAccent, modifier = Modifier.size(18.dp))
                    Text("YouTube GO", color = YgMuted, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.height(4.dp))
                Text("Tontonan yang lebih ringan.", color = YgMutedSoft, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LibraryTile(item: LibraryItem, onClick: (LibraryItem) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(YgSurfaceElevated)
            .border(1.dp, YgOutline.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .clickable { onClick(item) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(YgSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = null, tint = YgAccent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(item.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(item.hint, color = YgMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = YgMutedSoft)
    }
}
