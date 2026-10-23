package com.app.playtubelite.presentation.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.app.playtubelite.data.local.UserLibraryStore
import com.app.playtubelite.domain.model.ChannelInfo
import com.app.playtubelite.domain.repository.VideoRepository
import com.app.playtubelite.domain.usecase.toFriendlyMessage
import com.app.playtubelite.presentation.common.ErrorBox
import com.app.playtubelite.presentation.common.UiState
import com.app.playtubelite.presentation.common.VideoCardShimmer
import com.app.playtubelite.presentation.library.LibraryViewModel
import com.app.playtubelite.presentation.library.SubPageTopBar
import com.app.playtubelite.presentation.library.VideoItem
import com.app.playtubelite.presentation.theme.PtBackground
import com.app.playtubelite.presentation.theme.PtMuted
import com.app.playtubelite.presentation.theme.PtSurfaceVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: VideoRepository,
    private val library: UserLibraryStore,
) : ViewModel() {
    private val channelId: String = checkNotNull(savedState["channelId"])
    private val _state = MutableStateFlow<UiState<ChannelInfo>>(UiState.Loading)
    val state: StateFlow<UiState<ChannelInfo>> = _state.asStateFlow()
    val subscribed: StateFlow<Set<String>> = library.subscribed

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = repo.channel(channelId).fold(
                onSuccess = { info ->
                    // Sebagian instance mengirim latestVideos kosong di /channels/:id; /latest lebih andal.
                    val latest = info.latest.ifEmpty { repo.channelLatest(channelId).getOrDefault(emptyList()) }
                    UiState.Success(info.copy(latest = latest.distinctBy { it.id }))
                },
                onFailure = { UiState.Error(it.toFriendlyMessage()) },
            )
        }
    }

    fun toggleSubscribe() = library.toggleSubscribed(channelId)
}

@Composable
fun ChannelScreen(
    onBack: () -> Unit, onVideoClick: (String) -> Unit, onChannelClick: (String) -> Unit,
    vm: ChannelViewModel = hiltViewModel(), libVm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val subscribedIds by vm.subscribed.collectAsState()
    Scaffold(
        containerColor = PtBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SubPageTopBar((state as? UiState.Success)?.data?.name ?: "Channel", onBack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                is UiState.Loading -> LazyColumn(Modifier.fillMaxSize()) { items(3) { VideoCardShimmer() } }
                is UiState.Error -> ErrorBox(s.message) { vm.load() }
                is UiState.Success -> {
                    val c = s.data
                    val subscribed = c.id in subscribedIds
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        if (c.bannerUrl.isNotBlank()) item {
                            AsyncImage(c.bannerUrl, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 4f).background(PtSurfaceVariant))
                        }
                        item {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(c.avatarUrl, null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(CircleShape).background(PtSurfaceVariant))
                                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                    Text(c.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (c.subscriberText.isNotBlank()) Text(c.subscriberText, color = PtMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        item {
                            if (subscribed) FilledTonalButton(onClick = vm::toggleSubscribe, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(18.dp)); Text(" Terlangganan")
                            } else Button(
                                onClick = vm::toggleSubscribe, modifier = Modifier.padding(horizontal = 16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background),
                            ) { Text("Berlangganan") }
                        }
                        if (c.description.isNotBlank()) item {
                            Text(c.description, color = PtMuted, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(16.dp))
                        }
                        item {
                            Text("Video terbaru", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
                        }
                        if (c.latest.isEmpty()) item { Text("Channel ini belum menampilkan video.", color = PtMuted, modifier = Modifier.padding(16.dp)) }
                        items(c.latest, key = { it.id }, contentType = { "video" }) { v -> VideoItem(v, onVideoClick, onChannelClick, libVm) }
                    }
                }
            }
        }
    }
}
