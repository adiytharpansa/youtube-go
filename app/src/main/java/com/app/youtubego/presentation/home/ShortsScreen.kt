package com.app.youtubego.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.presentation.common.EmptyState
import com.app.youtubego.presentation.common.UiState
import com.app.youtubego.presentation.common.formatDuration
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgBackground
import com.app.youtubego.presentation.theme.YgMuted

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShortsScreen(
    onVideoClick: (String) -> Unit,
    vm: ShortsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Shorts", fontWeight = FontWeight.ExtraBold)
                        Text("BETA", color = YgAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                },
                actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Muat ulang") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YgBackground)
            )
        }
    ) { padding ->
        when (val content = state) {
            UiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = YgAccent)
            }
            is UiState.Error -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.PlayArrow,
                    title = "Shorts gagal dimuat",
                    message = content.message,
                    actionLabel = "Coba lagi",
                    onAction = vm::refresh
                )
            }
            is UiState.Success -> if (content.data.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.PlayArrow,
                    title = "Belum ada Shorts",
                    message = "Video pendek belum tersedia. Coba muat ulang sebentar lagi.",
                    actionLabel = "Muat ulang",
                    onAction = vm::refresh,
                    modifier = Modifier.padding(padding)
                )
            } else {
                ShortsPager(videos = content.data, modifier = Modifier.padding(padding), onVideoClick = onVideoClick)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortsPager(videos: List<VideoSummary>, modifier: Modifier = Modifier, onVideoClick: (String) -> Unit) {
    val pager = rememberPagerState(pageCount = { videos.size })
    VerticalPager(state = pager, modifier = modifier.fillMaxSize()) { page ->
        val video = videos[page]
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { onVideoClick(video.id) }) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(0.08f), Color.Transparent, Color.Black.copy(0.84f)))
                )
            )
            Box(
                Modifier.align(Alignment.Center).size(68.dp).clip(CircleShape).background(Color.Black.copy(0.55f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PlayArrow, "Putar video", tint = Color.White, modifier = Modifier.size(42.dp)) }
            ShortsInfo(video = video, index = page, total = videos.size, modifier = Modifier.align(Alignment.BottomStart))
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Text("Putar", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                Text("${formatDuration(video.lengthSeconds)}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ShortsInfo(video: VideoSummary, index: Int, total: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(0.78f).padding(start = 18.dp, end = 10.dp, bottom = 24.dp)
    ) {
        Text("${index + 1} / $total  ·  VIDEO PENDEK", color = Color.White.copy(0.78f), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(9.dp))
        Text(video.title, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Text("${video.author}  ·  ${video.viewCount} ditonton", color = Color.White.copy(0.78f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        Text("Ketuk video untuk memutar · Geser ke atas untuk berikutnya", color = Color.White.copy(0.72f), style = MaterialTheme.typography.labelSmall)
    }
}
