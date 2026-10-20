package com.app.youtubego.presentation.home
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.presentation.common.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onVideoClick: (String) -> Unit, onSearchClick: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube GO", fontWeight = FontWeight.ExtraBold) },
                actions = { IconButton(onClick = onSearchClick) { Icon(Icons.Default.Search, contentDescription = "Cari") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HomeCategory.entries) { cat ->
                    FilterChip(
                        selected = state.category == cat,
                        onClick = { vm.load(cat) },
                        label = { Text(when (cat) { HomeCategory.ALL -> "Semua"; HomeCategory.MUSIC -> "Musik"; HomeCategory.GAMING -> "Game"; HomeCategory.MIX -> "Mix"; HomeCategory.NEWS -> "Berita"; HomeCategory.LEARN -> "Belajar" }) },
                    )
                }
            }
            when (val s = state.videos) {
                is UiState.Loading -> LazyColumn(Modifier.fillMaxSize()) { items(5) { VideoCardShimmer() } }
                is UiState.Error -> ErrorBox(s.message) { vm.retry() }
                is UiState.Success -> if (s.data.isEmpty()) {
                    ErrorBox("Tidak ada video untuk kategori ini.") { vm.retry() }
                } else {
                    // key stabil + contentType: kunci scroll halus & reuse komposisi di daftar panjang.
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(s.data, key = { it.id }, contentType = { "video" }) { v -> VideoCard(v, onVideoClick) }
                    }
                }
            }
        }
    }
}
