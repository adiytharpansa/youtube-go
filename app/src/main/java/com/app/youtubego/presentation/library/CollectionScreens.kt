package com.app.youtubego.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.app.youtubego.data.local.DownloadItem
import com.app.youtubego.data.local.DownloadStatus
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.common.EmptyState
import com.app.youtubego.presentation.common.VideoAction
import com.app.youtubego.presentation.common.formatDuration
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgBackground
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurfaceVariant

/** Kerangka daftar video untuk halaman koleksi: bilah atas, keadaan kosong, daftar kartu dengan menu lengkap. */
@Composable
private fun VideoListScaffold(
    title: String,
    videos: List<VideoSummary>,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    vm: LibraryViewModel,
    topActions: @Composable RowScope.() -> Unit = {},
    extraActionsFor: (VideoSummary) -> List<VideoAction> = { emptyList() },
) {
    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SubPageTopBar(title, onBack, topActions) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (videos.isEmpty()) EmptyState(emptyIcon, emptyTitle, emptyMessage)
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
                items(videos, key = { it.id }, contentType = { "video" }) { v ->
                    VideoItem(v, onVideoClick, onChannelClick, vm, extraActions = extraActionsFor(v))
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, onVideoClick: (String) -> Unit, onChannelClick: (String) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val videos by vm.historyVideos.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    VideoListScaffold(
        title = "Riwayat", videos = videos, emptyIcon = Icons.Filled.History,
        emptyTitle = "Belum ada riwayat", emptyMessage = "Video yang kamu tonton akan muncul di sini.",
        onBack = onBack, onVideoClick = onVideoClick, onChannelClick = onChannelClick, vm = vm,
        topActions = {
            if (videos.isNotEmpty()) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Hapus semua riwayat") }
        },
        extraActionsFor = { v -> listOf(VideoAction("Hapus dari riwayat", Icons.Filled.Delete) { vm.removeHistory(v.id) }) },
    )
    if (confirm) ConfirmDialog("Hapus semua riwayat?", "Daftar video yang pernah kamu tonton akan dikosongkan.", "Hapus", { vm.clearHistory() }) { confirm = false }
}

@Composable
fun WatchLaterScreen(onBack: () -> Unit, onVideoClick: (String) -> Unit, onChannelClick: (String) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val videos by vm.savedVideos.collectAsState()
    VideoListScaffold(
        title = "Tonton nanti", videos = videos, emptyIcon = Icons.Filled.WatchLater,
        emptyTitle = "Belum ada video tersimpan", emptyMessage = "Ketuk titik tiga pada video lalu pilih Tonton nanti.",
        onBack = onBack, onVideoClick = onVideoClick, onChannelClick = onChannelClick, vm = vm,
    )
}

@Composable
fun LikedScreen(onBack: () -> Unit, onVideoClick: (String) -> Unit, onChannelClick: (String) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val videos by vm.likedVideos.collectAsState()
    VideoListScaffold(
        title = "Video disukai", videos = videos, emptyIcon = Icons.Filled.ThumbUp,
        emptyTitle = "Belum ada video disukai", emptyMessage = "Ketuk Suka di halaman pemutar untuk menyimpannya di sini.",
        onBack = onBack, onVideoClick = onVideoClick, onChannelClick = onChannelClick, vm = vm,
        extraActionsFor = { v -> listOf(VideoAction("Hapus dari disukai", Icons.Filled.ThumbUp) { vm.toggleLiked(v) }) },
    )
}

// ---------------------------------------------------------------- Playlist

@Composable
fun PlaylistsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val playlists by vm.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SubPageTopBar("Playlist", onBack) {
                IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, contentDescription = "Playlist baru") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (playlists.isEmpty()) {
                EmptyState(
                    Icons.AutoMirrored.Filled.PlaylistPlay, "Belum ada playlist", "Kumpulkan video favoritmu dalam satu daftar.",
                    actionLabel = "Buat playlist", onAction = { creating = true },
                )
            } else LazyColumn(Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { p ->
                    var menu by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = YgAccent)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.videoIds.size} video", color = YgMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Opsi playlist", tint = YgMuted) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Ubah nama") }, onClick = { menu = false; renaming = p.id })
                                DropdownMenuItem(text = { Text("Hapus playlist") }, onClick = { menu = false; deleting = p.id })
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) NameDialog("Playlist baru", "", "Buat", { vm.createPlaylist(it) }) { creating = false }
    renaming?.let { id ->
        NameDialog("Ubah nama playlist", playlists.firstOrNull { it.id == id }?.name.orEmpty(), "Simpan", { vm.renamePlaylist(id, it) }) { renaming = null }
    }
    deleting?.let { id ->
        ConfirmDialog("Hapus playlist?", "Playlist dihapus, videonya tidak ikut terhapus dari perangkat.", "Hapus", { vm.deletePlaylist(id) }) { deleting = null }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = com.app.youtubego.presentation.theme.YgSurface,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("Nama playlist") }) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun PlaylistDetailScreen(
    playlistId: String, onBack: () -> Unit, onVideoClick: (String) -> Unit, onChannelClick: (String) -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val playlists by vm.playlists.collectAsState()
    val name = playlists.firstOrNull { it.id == playlistId }?.name ?: "Playlist"
    val videos by remember(playlistId) { vm.playlistVideos(playlistId) }.collectAsState(initial = emptyList())
    // Playlist dihapus dari tempat lain -> kembali.
    LaunchedEffect(playlists) { if (playlists.none { it.id == playlistId }) onBack() }
    // Memutar dari playlist: Berikutnya/Sebelumnya di pemutar mengikuti urutan daftar ini.
    val play: (String) -> Unit = { id -> vm.startPlaylist(name, videos); onVideoClick(id) }
    VideoListScaffold(
        title = name, videos = videos, emptyIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
        emptyTitle = "Playlist masih kosong", emptyMessage = "Ketuk titik tiga pada video lalu pilih Simpan ke playlist.",
        onBack = onBack, onVideoClick = play, onChannelClick = onChannelClick, vm = vm,
        topActions = {
            if (videos.isNotEmpty()) IconButton(onClick = { play(videos.first().id) }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Putar dari awal") }
        },
        extraActionsFor = { v -> listOf(VideoAction("Hapus dari playlist", Icons.Filled.Delete) { vm.removeFromPlaylist(playlistId, v.id) }) },
    )
}

// ---------------------------------------------------------------- Unduhan

@Composable
fun DownloadsScreen(onBack: () -> Unit, onPlayOffline: (String) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val items by vm.downloadItems.collectAsState()
    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SubPageTopBar("Unduhan", onBack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (items.isEmpty()) {
                EmptyState(Icons.Filled.Download, "Belum ada unduhan", "Ketuk titik tiga pada video lalu pilih Unduh untuk menonton tanpa internet.")
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { it.video.id }) { d -> DownloadRow(d, vm, onPlayOffline) }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: DownloadItem, vm: LibraryViewModel, onPlayOffline: (String) -> Unit) {
    val v = d.video
    val done = d.status == DownloadStatus.DONE
    Row(
        Modifier.fillMaxWidth().clickable(enabled = done) { onPlayOffline(v.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small).background(YgSurfaceVariant)) {
            AsyncImage(v.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (v.lengthSeconds > 0) Box(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(0.78f)).padding(horizontal = 4.dp, vertical = 1.dp)
            ) { Text(formatDuration(v.lengthSeconds), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val status = when (d.status) {
                DownloadStatus.DONE -> "${formatSize(d.bytes)} • Siap ditonton offline"
                DownloadStatus.DOWNLOADING -> if (d.total > 0) "Mengunduh ${(d.progress * 100).toInt()}% • ${formatSize(d.bytes)}" else "Mengunduh..."
                DownloadStatus.PAUSED -> "Dijeda • ${formatSize(d.bytes)}"
                DownloadStatus.FAILED -> d.error.ifBlank { "Gagal mengunduh" }
            }
            Text(status, color = if (d.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else YgMuted,
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (d.status == DownloadStatus.DOWNLOADING) {
                if (d.total > 0) LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = YgAccent)
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), color = YgAccent)
            }
        }
        when (d.status) {
            DownloadStatus.DOWNLOADING -> IconButton(onClick = { vm.pauseDownload(v.id) }) { Icon(Icons.Filled.Pause, contentDescription = "Jeda") }
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> IconButton(onClick = { vm.download(v) }) { Icon(Icons.Filled.Refresh, contentDescription = "Lanjutkan") }
            DownloadStatus.DONE -> Unit
        }
        IconButton(onClick = { vm.removeDownload(v.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Hapus unduhan", tint = YgMuted) }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.0f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
