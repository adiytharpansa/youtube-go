package com.app.youtubego.presentation.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
fun SubscriptionsScreen(
    onExplore: () -> Unit,
    onVideoClick: (String) -> Unit,
    vm: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column {
                        Text("Langganan")
                        if (state.channelCount > 0) Text(
                            "${state.channelCount} channel diikuti",
                            color = YgMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YgBackground)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            when (val videos = state.videos) {
                is UiState.Loading -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp)) {
                    items(4) { VideoCardShimmer() }
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorBox(videos.message, vm::refresh)
                }
                is UiState.Success -> if (videos.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Subscriptions,
                        title = if (state.channelCount == 0) "Mulai ikuti channel" else "Belum ada video baru",
                        message = if (state.channelCount == 0)
                            "Berlangganan channel dari halaman video. Video terbarunya akan muncul di sini."
                        else "Channel yang kamu ikuti belum mengunggah video terbaru.",
                        actionLabel = if (state.channelCount == 0) "Jelajahi video" else "Muat ulang",
                        onAction = if (state.channelCount == 0) onExplore else vm::refresh
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)
                    ) {
                        item {
                            YgSectionHeader("Terbaru dari channel", "Video dari channel yang kamu ikuti")
                        }
                        items(videos.data, key = { it.id }, contentType = { "video" }) { video ->
                            VideoCardPro(video = video, onClick = onVideoClick)
                        }
                    }
                }
            }
        }
    }
}
