package com.app.youtubego.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.app.youtubego.domain.model.ChannelInfo
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    vm: ChannelViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val subscriptions by vm.subscribed.collectAsState()
    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Channel") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YgBackground)
            )
        }
    ) { padding ->
        when (val content = state) {
            UiState.Loading -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(top = 12.dp)) {
                items(3) { VideoCardShimmer() }
            }
            is UiState.Error -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorBox(content.message, vm::refresh)
            }
            is UiState.Success -> {
                val channel = content.data
                LazyColumn(
                    Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item { ChannelHeader(channel, channel.id in subscriptions, vm::toggleSubscribe) }
                    item { YgSectionHeader("Video terbaru", "${channel.latest.size} video") }
                    if (channel.latest.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Default.Subscriptions,
                                title = "Belum ada video",
                                message = "Channel ini belum menampilkan video terbaru."
                            )
                        }
                    }
                    items(channel.latest, key = { it.id }, contentType = { "video" }) { video ->
                        VideoCardPro(video = video, onClick = onVideoClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(info: ChannelInfo, subscribed: Boolean, onSubscribe: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (info.bannerUrl.isNotBlank()) {
            AsyncImage(
                model = info.bannerUrl,
                contentDescription = "Banner ${info.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(118.dp).background(YgSurfaceVariant)
            )
        } else {
            Box(Modifier.fillMaxWidth().height(88.dp).background(YgGradientBgTop))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = info.avatarUrl,
                contentDescription = info.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(68.dp).clip(CircleShape).background(YgSurfaceVariant)
            )
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(info.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(info.subscriberText.ifBlank { "Channel YouTube" }, color = YgMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (subscribed) {
                FilledTonalButton(onClick = onSubscribe) {
                    Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(18.dp))
                    Text("  Mengikuti")
                }
            } else {
                Button(onClick = onSubscribe, colors = ButtonDefaults.buttonColors(containerColor = YgAccent)) {
                    Icon(Icons.Default.Subscriptions, null, modifier = Modifier.size(18.dp))
                    Text("  Berlangganan")
                }
            }
        }
        if (info.description.isNotBlank()) {
            Text(
                info.description,
                color = YgMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
    }
}
