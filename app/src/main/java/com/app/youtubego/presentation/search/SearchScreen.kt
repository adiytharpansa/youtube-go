package com.app.youtubego.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.theme.*

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit = {},
    vm: SearchViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Column(
        Modifier.fillMaxSize().background(YgBackground).statusBarsPadding()
    ) {
        // Search header, surface pill + back affordance
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(YgSurfaceVariant)
                    .border(1.dp, YgOutline, CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = YgOnSurface)
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
                placeholder = { Text("Cari video, channel, topik…", color = YgMutedSoft) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = YgMuted) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = YgMuted)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = YgSurfaceElevated,
                    unfocusedContainerColor = YgSurfaceVariant,
                    focusedBorderColor = YgAccent.copy(alpha = 0.6f),
                    unfocusedBorderColor = YgOutline,
                    cursorColor = YgAccent,
                    focusedTextColor = YgOnSurface,
                    unfocusedTextColor = YgOnSurface,
                ),
                modifier = Modifier.weight(1f),
            )
        }

        // Search hero / filters
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Jelajahi", style = MaterialTheme.typography.headlineSmall, color = YgOnSurface)
                Text("Temukan tontonan berikutnya", color = YgMuted, style = MaterialTheme.typography.bodySmall)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(YgGradientAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Explore, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            YgChip("Video", selected = !state.shortsOnly, onClick = { vm.setShortsOnly(false) })
            YgChip("Shorts", selected = state.shortsOnly, onClick = { vm.setShortsOnly(true) })
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(YgDivider))

        when {
            state.suggestions.isNotEmpty() -> {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(state.suggestions) { suggestion ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.submit(suggestion) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.History, null, tint = YgMuted, modifier = Modifier.size(18.dp))
                            }
                            Text(suggestion, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.NorthWest, null, tint = YgMutedSoft, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            state.query.isBlank() -> {
                // Initial prompt
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(70.dp).clip(CircleShape).background(Brush.radialGradient(listOf(YgAccent.copy(0.24f), Color.Transparent))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Search, null, tint = YgAccent, modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Apa yang ingin kamu tonton?", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Cari kreator, musik, game, tutorial, dan lainnya.", color = YgMuted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
            else -> when (val result = state.results) {
                is UiState.Loading -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp)) { items(4) { VideoCardShimmer() } }
                is UiState.Error -> ErrorBox(result.message) { vm.submit() }
                is UiState.Success -> if (result.data.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "Belum ada hasil",
                        message = "Coba kata kunci lain atau periksa ejaan pencarianmu.",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)) {
                        item { YgSectionHeader("Hasil untuk ‘${state.query}’", "${result.data.size} video ditemukan") }
                        items(result.data, key = { it.id }, contentType = { "video" }) { video ->
                            VideoCardPro(video, onVideoClick, onChannelClick = onChannelClick)
                        }
                    }
                }
            }
        }
    }
}
