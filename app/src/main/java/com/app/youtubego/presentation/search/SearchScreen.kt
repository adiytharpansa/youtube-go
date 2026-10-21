package com.app.youtubego.presentation.search
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.library.LibraryViewModel
import com.app.youtubego.presentation.library.VideoItem
@Composable
fun SearchScreen(onBack: () -> Unit, onVideoClick: (String) -> Unit, onChannelClick: (String) -> Unit = {}, vm: SearchViewModel = hiltViewModel(), libVm: LibraryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            OutlinedTextField(
                value = state.query, onValueChange = vm::onQueryChange, singleLine = true,
                placeholder = { Text("Cari video...") }, trailingIcon = { IconButton(onClick = { vm.submit() }) { Icon(Icons.Default.Search, null) } },
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !state.shortsOnly, onClick = { vm.setShortsOnly(false) }, label = { Text("Video") })
            FilterChip(selected = state.shortsOnly, onClick = { vm.setShortsOnly(true) }, label = { Text("Shorts") })
        }
        if (state.suggestions.isNotEmpty()) {
            LazyColumn { items(state.suggestions) { s -> ListItem(headlineContent = { Text(s) }, leadingContent = { Icon(Icons.Default.Search, null) }, modifier = Modifier.clickable { vm.submit(s) }) } }
            return@Column
        }
        when (val s = state.results) {
            is UiState.Loading -> LazyColumn { items(4) { VideoCardShimmer() } }
            is UiState.Error -> ErrorBox(s.message) { vm.submit() }
            is UiState.Success -> LazyColumn(Modifier.fillMaxSize()) { items(s.data, key = { it.id }) { v -> VideoItem(v, onVideoClick, onChannelClick, libVm) } }
        }
    }
}

