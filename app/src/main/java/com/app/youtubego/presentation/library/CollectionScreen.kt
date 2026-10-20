package com.app.youtubego.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.theme.YgBackground
import com.app.youtubego.presentation.theme.YgMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    vm: CollectionViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    Scaffold(
        containerColor = YgBackground,
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YgBackground)
            )
        }
    ) { padding ->
        when {
            state.loading -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(top = 12.dp)) {
                items(4) { VideoCardShimmer() }
            }
            state.unsupported -> EmptyState(
                icon = if (state.title == "Unduhan") Icons.Default.WatchLater else Icons.AutoMirrored.Filled.PlaylistPlay,
                title = "Belum tersedia",
                message = state.message,
                modifier = Modifier.padding(padding)
            )
            state.videos.isEmpty() -> EmptyState(
                icon = when (state.title) {
                    "Riwayat tontonan" -> Icons.Default.History
                    "Video disukai" -> Icons.AutoMirrored.Filled.PlaylistPlay
                    else -> Icons.Default.WatchLater
                },
                title = "Koleksi masih kosong",
                message = when (state.title) {
                    "Riwayat tontonan" -> "Video yang kamu tonton akan muncul di sini."
                    "Video disukai" -> "Video yang kamu sukai akan tersimpan di sini."
                    else -> "Simpan video untuk ditonton nanti."
                },
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
            ) {
                item {
                    Text(
                        "${state.videos.size} video",
                        color = YgMuted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(state.videos, key = { it.id }) { video ->
                    VideoCardPro(
                        video = video,
                        onClick = onVideoClick,
                        actions = listOf(
                            VideoAction(
                                label = if (state.title == "Riwayat tontonan") "Hapus dari riwayat" else "Hapus dari koleksi",
                                icon = Icons.Default.DeleteOutline,
                                onClick = { vm.remove(video.id) }
                            )
                        )
                    )
                }
            }
        }
    }
}
