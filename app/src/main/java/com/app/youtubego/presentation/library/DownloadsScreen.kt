package com.app.youtubego.presentation.library

import android.app.DownloadManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.youtubego.data.local.OfflineVideo
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit, vm: DownloadsViewModel = hiltViewModel()) {
    val records by vm.records.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        while (true) {
            vm.refresh()
            delay(1500)
        }
    }
    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Unduhan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YgBackground)
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Download,
                title = "Belum ada unduhan",
                message = "Buka video lalu pilih Unduh untuk menyimpannya di perangkat.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)) {
                item { YgSectionHeader("Video offline", "Unduhan tersimpan di perangkat ini") }
                items(records, key = { it.downloadId }) { item ->
                    DownloadCard(
                        item = item,
                        onOpen = {
                            item.downloadedUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(Intent.createChooser(intent, "Putar video")) }
                            }
                        },
                        onRemove = { vm.remove(item.downloadId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(item: OfflineVideo, onOpen: () -> Unit, onRemove: () -> Unit) {
    val (status, tint) = when (item.status) {
        DownloadManager.STATUS_SUCCESSFUL -> "Siap ditonton offline" to YgSuccess
        DownloadManager.STATUS_RUNNING -> "Mengunduh · ${item.progress}%" to YgAccent
        DownloadManager.STATUS_PENDING -> "Menunggu unduhan" to YgMuted
        DownloadManager.STATUS_PAUSED -> "Unduhan dijeda · ${item.progress}%" to YgMuted
        else -> "Unduhan gagal" to MaterialTheme.colorScheme.error
    }
    Column(Modifier.fillMaxWidth()) {
        VideoCardPro(
            video = item.video,
            onClick = { if (item.status == DownloadManager.STATUS_SUCCESSFUL) onOpen() },
            actions = listOf(VideoAction("Hapus unduhan", Icons.Default.DeleteOutline, onRemove))
        )
        Row(Modifier.fillMaxWidth().padding(start = 68.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(status, color = tint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
                TextButton(onClick = onOpen) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Text(" Putar")
                }
            } else if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING) {
                CircularProgressIndicator(progress = item.progress / 100f, modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = YgAccent)
            }
        }
        if (item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PENDING || item.status == DownloadManager.STATUS_PAUSED) {
            LinearProgressIndicator(
                progress = item.progress / 100f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(3.dp),
                color = YgAccent,
                trackColor = YgSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}
