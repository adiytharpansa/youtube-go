package com.app.youtubego.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.youtubego.data.local.LocalPlaylist
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    vm: PlaylistHomeViewModel = hiltViewModel(),
) {
    val playlists by vm.items.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Playlist") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                actions = { IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "Buat playlist") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YgBackground)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Playlist baru") },
                containerColor = YgAccent,
                contentColor = androidx.compose.ui.graphics.Color.White
            )
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                title = "Buat playlist pertamamu",
                message = "Kumpulkan video untuk menyusun daftar tontonan sendiri.",
                actionLabel = "Buat playlist",
                onAction = { showCreate = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
                item { YgSectionHeader("Playlist lokal", "Tersimpan hanya di perangkat ini") }
                items(playlists, key = { it.id }) { playlist -> PlaylistTile(playlist, onClick = { onOpen(playlist.id) }, onDelete = { vm.delete(playlist.id) }) }
            }
        }
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Buat playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    singleLine = true,
                    label = { Text("Nama playlist") },
                    placeholder = { Text("Misalnya: Video favorit") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.create(name)?.let(onOpen)
                    name = ""
                    showCreate = false
                }, enabled = name.isNotBlank()) { Text("Buat") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Batal") } },
            containerColor = YgSurface
        )
    }
}

@Composable
private fun PlaylistTile(playlist: LocalPlaylist, onClick: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(playlist.name) },
        supportingContent = { Text("${playlist.videoIds.size} video · playlist lokal") },
        leadingContent = {
            Box(Modifier.size(46.dp).background(YgSurfaceVariant, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = YgAccent)
            }
        },
        trailingContent = { IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Hapus playlist", tint = YgMuted) } },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = YgSurface)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    vm: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(state.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YgBackground)
            )
        }
    ) { padding ->
        when {
            state.loading -> LazyColumn(Modifier.padding(padding).fillMaxSize()) { items(4) { VideoCardShimmer() } }
            state.videos.isEmpty() -> EmptyState(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                title = "Playlist masih kosong",
                message = "Tambahkan video dari pemutar dengan memilih ikon playlist.",
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)) {
                items(state.videos, key = { it.id }) { video ->
                    VideoCardPro(
                        video = video,
                        onClick = onVideoClick,
                        actions = listOf(VideoAction("Hapus dari playlist", Icons.Default.DeleteOutline) { vm.remove(video.id) })
                    )
                }
            }
        }
    }
}
