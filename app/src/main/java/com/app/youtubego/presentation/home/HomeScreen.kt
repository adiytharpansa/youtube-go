package com.app.youtubego.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.app.youtubego.R
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.theme.*

private fun catLabel(cat: HomeCategory): String = when (cat) {
    HomeCategory.FOR_YOU -> "Untuk Anda"
    HomeCategory.ALL -> "Semua"
    HomeCategory.MUSIC -> "Musik"
    HomeCategory.GAMING -> "Game"
    HomeCategory.MIX -> "Mix"
    HomeCategory.NEWS -> "Berita"
    HomeCategory.LEARN -> "Belajar"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val profile by vm.profile.collectAsState()
    val listState = rememberLazyListState()

    LifecycleResumeEffect(Unit) { vm.refreshIfStale(); onPauseOrDispose { } }

    if (state.showInterestSheet) {
        InterestSheet(
            profile = profile, onToggleSeed = vm::toggleSeed, onForgetTopic = vm::forgetTopic,
            onForgetChannel = vm::forgetChannel, onReset = vm::resetInterests, onDismiss = vm::closeInterests
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = YgBackground,
        topBar = {
            YgHomeTopBar(onSearchClick = onSearchClick, onInterestClick = vm::openInterests)
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // ── Chip kategori — gradien saat terpilih, scroll halus ──
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(HomeCategory.entries) { cat ->
                    YgChip(
                        label = catLabel(cat),
                        selected = state.category == cat,
                        onClick = { vm.load(cat) }
                    )
                }
            }
            // Garis pemisah halus
            Box(Modifier.fillMaxWidth().height(1.dp).background(YgDivider.copy(alpha = 0.6f)))

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = state.videos) {
                    is UiState.Loading -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 10.dp)) {
                        items(5) { VideoCardShimmer() }
                    }
                    is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Box(
                                Modifier.size(72.dp).clip(CircleShape).background(YgSurfaceVariant)
                                    .border(1.dp, YgOutline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.WifiOff, null, tint = YgMuted, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(s.message, color = YgMuted, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(18.dp))
                            GradientButton(label = "Coba lagi", onClick = { vm.retry() })
                        }
                    }
                    is UiState.Success -> if (s.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                icon = Icons.Outlined.SlowMotionVideo,
                                title = "Belum ada video",
                                message = "Tidak ada video untuk kategori ini. Coba kategori lain atau ubah minatmu.",
                                actionLabel = "Muat ulang",
                                onAction = { vm.retry() }
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                        ) {
                            items(s.data, key = { it.id }, contentType = { "video" }) { v ->
                                VideoCardPro(video = v, onClick = onVideoClick, onChannelClick = onChannelClick)
                            }
                            item {
                                // Footer halus
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(Modifier.width(36.dp).height(3.dp).clip(CircleShape).background(YgOutline))
                                    Spacer(Modifier.height(8.dp))
                                    Text("Kamu sudah di akhir daftar — tarik untuk segarkan ✨", color = YgMutedSoft, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YgHomeTopBar(
    onSearchClick: () -> Unit,
    onInterestClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(YgBackground)) {
        // Bar utama — logo + search pill + avatar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Logo dengan badge GO
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(YgGradientAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("YouTube", color = YgOnSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, lineHeight = 16.sp)
                    Text("GO", color = YgAccent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, lineHeight = 11.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            // Tombol minat — pill dengan ikon Tune
            Box(
                Modifier.height(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(YgSurfaceVariant).border(1.dp, YgOutline, RoundedCornerShape(12.dp))
                    .clickable(onClick = onInterestClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = YgMuted, modifier = Modifier.size(16.dp))
                    Text("Minat", color = YgMuted, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }

            // Search pill — seperti YouTube
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(YgSurfaceVariant)
                    .border(1.dp, YgOutline, CircleShape)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = "Cari", tint = YgOnSurface, modifier = Modifier.size(20.dp))
            }

            // Avatar profil dengan ring gradient
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(YgGradientAccent).padding(1.5.dp)
            ) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(YgSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = YgOnSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
