package com.app.youtubego.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.app.youtubego.domain.model.CommentItem
import com.app.youtubego.data.local.LocalPlaylist
import com.app.youtubego.domain.model.VideoDetail
import com.app.youtubego.presentation.common.UiState
import com.app.youtubego.presentation.common.formatRelativeTime
import com.app.youtubego.presentation.common.formatViewsId
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurface
import com.app.youtubego.presentation.theme.YgSurfaceVariant

internal data class Choice(val label: String, val selected: Boolean, val onPick: () -> Unit)
private enum class SettingsPage { ROOT, QUALITY, SPEED, CAPTIONS }

/** Panel pengaturan pemutar: Kualitas, Kecepatan pemutaran, Teks (CC). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSettingsSheet(
    qualityLabel: String, qualityChoices: List<Choice>,
    speedLabel: String, speedChoices: List<Choice>,
    captionLabel: String, captionChoices: List<Choice>,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YgSurface) {
        when (page) {
            SettingsPage.ROOT -> {
                if (qualityChoices.isNotEmpty()) SettingRow(Icons.Default.Tune, "Kualitas", qualityLabel) { page = SettingsPage.QUALITY }
                SettingRow(Icons.Default.Speed, "Kecepatan pemutaran", speedLabel) { page = SettingsPage.SPEED }
                if (captionChoices.isNotEmpty()) SettingRow(Icons.Default.ClosedCaption, "Teks (CC)", captionLabel) { page = SettingsPage.CAPTIONS }
            }
            else -> {
                val (title, choices) = when (page) {
                    SettingsPage.QUALITY -> "Kualitas" to qualityChoices
                    SettingsPage.SPEED -> "Kecepatan pemutaran" to speedChoices
                    else -> "Teks (CC)" to captionChoices
                }
                ListItem(
                    headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.Default.KeyboardArrowLeft, "Kembali") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { page = SettingsPage.ROOT },
                )
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { c ->
                        ListItem(
                            headlineContent = { Text(c.label) },
                            leadingContent = { if (c.selected) Icon(Icons.Default.Check, null, tint = YtRed) else Spacer(Modifier.size(24.dp)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { c.onPick(); onDismiss() },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Text(value, color = YgMuted) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Panel komentar (bottom sheet) dari API komentar Invidious. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(comments: UiState<List<CommentItem>>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YgSurface) {
        Text("Komentar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        when (comments) {
            is UiState.Loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Text(comments.message, color = YgMuted, modifier = Modifier.padding(16.dp))
            is UiState.Success -> if (comments.data.isEmpty()) {
                Text("Belum ada komentar atau komentar dinonaktifkan.", color = YgMuted, modifier = Modifier.padding(16.dp))
            } else LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(comments.data) { c ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        AsyncImage(c.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(34.dp).clip(CircleShape).background(YgSurfaceVariant))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(listOf(c.author, formatRelativeTime(c.published)).filter { it.isNotBlank() }.joinToString(" • "), color = YgMuted, style = MaterialTheme.typography.labelMedium)
                            Text(c.content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                            if (c.likeCount > 0) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ThumbUp, null, tint = YgMuted, modifier = Modifier.size(14.dp))
                                Text(" ${formatViewsId(c.likeCount)}", color = YgMuted, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Panel deskripsi lengkap ("...selengkapnya"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DescriptionSheet(d: VideoDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YgSurface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(d.summary.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(if (d.likeCount > 0) formatViewsId(d.likeCount) else "-", "suka")
                Stat(formatViewsId(d.summary.viewCount), "tontonan")
                formatRelativeTime(d.summary.published).takeIf { it.isNotEmpty() }?.let { Stat(it, "diunggah") }
            }
            Text(d.description.ifBlank { "Tidak ada deskripsi." }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, color = YgMuted, style = MaterialTheme.typography.labelSmall)
    }
}

/** Pilih playlist lokal atau buat playlist baru sambil menyimpan video aktif. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistPickerSheet(
    playlists: List<LocalPlaylist>,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YgSurface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Simpan ke playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (playlists.isEmpty()) {
                Text("Belum ada playlist. Buat satu untuk menyimpan video ini.", color = YgMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
            } else {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    playlists.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.videoIds.size} video", color = YgMuted) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = YtRed) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onPick(playlist.id) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                singleLine = true,
                label = { Text("Nama playlist baru") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onCreate(name); name = "" },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            ) { Text("Buat dan simpan") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
