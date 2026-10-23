package com.app.playtubelite.presentation.library

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.app.playtubelite.domain.model.Playlist
import com.app.playtubelite.domain.model.VideoSummary
import com.app.playtubelite.presentation.common.VideoAction
import com.app.playtubelite.presentation.common.VideoCardPro
import com.app.playtubelite.presentation.theme.PtBackground
import com.app.playtubelite.presentation.theme.PtMuted
import com.app.playtubelite.presentation.theme.PtSurface

/**
 * Kartu video + menu titik tiga lengkap (Tonton nanti, Simpan ke playlist, Unduh, Bagikan) yang dipakai
 * di SEMUA daftar video: Beranda, Cari, Langganan, Channel, Pemutar, dan halaman koleksi.
 */
@Composable
fun VideoItem(
    video: VideoSummary,
    onClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
    vm: LibraryViewModel,
    modifier: Modifier = Modifier,
    extraActions: List<VideoAction> = emptyList(),
) {
    val context = LocalContext.current
    val savedState = vm.saved.collectAsState()
    val downloadsState = vm.downloadItems.collectAsState()
    // derivedStateOf: kartu hanya disusun ulang bila status video INI berubah, bukan tiap progres unduhan bergerak.
    val isSaved by remember(video.id) { derivedStateOf { video.id in savedState.value } }
    val isDownloaded by remember(video.id) {
        derivedStateOf { downloadsState.value.any { it.video.id == video.id && it.status == com.app.playtubelite.data.local.DownloadStatus.DONE } }
    }
    val progressState = vm.progress.collectAsState()
    val watched by remember(video.id, video.lengthSeconds) {
        derivedStateOf {
            val ms = progressState.value[video.id] ?: 0L
            if (ms > 0L && video.lengthSeconds > 0L && !video.isLive) (ms / 1000f / video.lengthSeconds).coerceIn(0f, 1f) else 0f
        }
    }
    var pickerOpen by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val actions = buildList {
        add(VideoAction(if (isSaved) "Hapus dari Tonton nanti" else "Tonton nanti", Icons.Filled.WatchLater) {
            vm.toggleSaved(video); toast(if (isSaved) "Dihapus dari Tonton nanti" else "Disimpan ke Tonton nanti")
        })
        add(VideoAction("Tambahkan ke antrean", Icons.AutoMirrored.Filled.PlaylistPlay) {
            toast(if (vm.enqueue(video)) "Ditambahkan ke antrean" else "Sudah ada di antrean")
        })
        add(VideoAction("Simpan ke playlist", Icons.AutoMirrored.Filled.PlaylistAdd) { pickerOpen = true })
        add(
            if (isDownloaded) VideoAction("Hapus unduhan", Icons.Filled.DownloadDone) { vm.removeDownload(video.id); toast("Unduhan dihapus") }
            else VideoAction("Unduh", Icons.Filled.Download) { vm.download(video); toast("Mulai mengunduh. Lihat di Anda > Unduhan") },
        )
        add(VideoAction("Bagikan", Icons.Filled.Share) {
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${video.title}\nhttps://youtu.be/${video.id}") }
            context.startActivity(Intent.createChooser(send, "Bagikan video"))
        })
        addAll(extraActions)
    }
    VideoCardPro(video, onClick, modifier, onChannelClick = onChannelClick, actions = actions, watchedFraction = watched)

    if (pickerOpen) {
        val playlists by vm.playlists.collectAsState()
        PlaylistPickerDialog(
            playlists = playlists, videoId = video.id,
            onToggle = { vm.togglePlaylistVideo(it.id, video) },
            onCreate = { name -> vm.createPlaylist(name, video); toast("Playlist dibuat") },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>, videoId: String,
    onToggle: (Playlist) -> Unit, onCreate: (String) -> Unit, onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PtSurface,
        title = { Text("Simpan ke playlist") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (playlists.isEmpty() && !creating) Text("Belum ada playlist. Buat yang pertama.", color = PtMuted)
                playlists.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { onToggle(p) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = videoId in p.videoIds, onCheckedChange = { onToggle(p) })
                        Text(p.name, maxLines = 1)
                    }
                }
                if (creating) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                        label = { Text("Nama playlist") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    TextButton(onClick = { creating = true }) { Text("+ Playlist baru") }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton(onClick = { if (name.isNotBlank()) { onCreate(name); name = ""; creating = false } }) { Text("Buat") }
            else TextButton(onClick = onDismiss) { Text("Selesai") }
        },
        dismissButton = { if (creating) TextButton(onClick = { creating = false; name = "" }) { Text("Batal") } },
    )
}

/** Bilah atas standar halaman dalam: tombol kembali + judul + aksi opsional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPageTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = PtBackground),
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, destructive: Boolean = false, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = PtSurface,
        title = { Text(title) }, text = { Text(message, color = PtMuted) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
