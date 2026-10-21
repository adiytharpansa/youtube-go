package com.app.youtubego.presentation.home
import com.app.youtubego.presentation.navigation.LocalTabReselect
import com.app.youtubego.presentation.navigation.Routes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.app.youtubego.R
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.presentation.common.*
import com.app.youtubego.presentation.library.LibraryViewModel
import com.app.youtubego.presentation.library.VideoItem
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurface
import com.app.youtubego.presentation.theme.YgSurfaceVariant
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit, onSearchClick: () -> Unit, onChannelClick: (String) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(), libVm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val profile by vm.profile.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(state.category) { listState.scrollToItem(0) }
    // Ketuk ulang tab "Beranda" saat sudah aktif: gulir ke paling atas.
    val reselect = LocalTabReselect.current
    LaunchedEffect(reselect) { reselect.collect { if (it == Routes.HOME) listState.animateScrollToItem(0) } }
    // Beranda selalu mengikuti minat: tiap kembali tampil (mis. setelah menonton/mencari), feed disegarkan bila profil berubah.
    LifecycleResumeEffect(Unit) { vm.refreshIfStale(); onPauseOrDispose { } }
    if (state.showInterestSheet) {
        InterestSheet(profile = profile, onToggleSeed = vm::toggleSeed, onForgetTopic = vm::forgetTopic,
            onForgetChannel = vm::forgetChannel, onReset = vm::resetInterests, onDismiss = vm::closeInterests)
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val tonalColors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = YgSurfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Image(painterResource(R.drawable.ic_logo_mark), contentDescription = null, modifier = Modifier.size(34.dp))
                        Text("YouTube GO", fontWeight = FontWeight.ExtraBold, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = vm::openInterests, colors = tonalColors) { Icon(Icons.Outlined.Tune, contentDescription = "Atur minat") }
                    FilledTonalIconButton(onClick = onSearchClick, colors = tonalColors) { Icon(Icons.Outlined.Search, contentDescription = "Cari") }
                    Spacer(Modifier.width(8.dp))
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            LazyRow(Modifier.padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HomeCategory.entries) { cat ->
                    YgChip(label = when (cat) { HomeCategory.FOR_YOU -> "Untuk Anda"; HomeCategory.ALL -> "Semua"; HomeCategory.MUSIC -> "Musik"; HomeCategory.GAMING -> "Game"; HomeCategory.MIX -> "Mix"; HomeCategory.NEWS -> "Berita"; HomeCategory.LEARN -> "Belajar" }, selected = state.category == cat, onClick = { vm.load(cat) })
                }
            }
            (state.videos as? UiState.Success)?.data?.let { PrefetchThumbnails(it) }
            // Indikator tarik-untuk-segarkan bergaya aplikasi (bukan lingkaran abu gelap bawaan).
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.refreshing, onRefresh = vm::refresh, state = pullState, modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState, isRefreshing = state.refreshing, modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = YgSurface, color = YgAccent,
                    )
                },
            ) {
                when (val s = state.videos) {
                    is UiState.Loading -> LazyColumn(Modifier.fillMaxSize()) { items(5) { VideoCardShimmer() } }
                    is UiState.Error -> ErrorBox(s.message) { vm.retry() }
                    is UiState.Success -> if (s.data.isEmpty()) {
                        ErrorBox("Tidak ada video untuk kategori ini.") { vm.retry() }
                    } else {
                        // Scroll tak terbatas: minta halaman berikutnya saat 4 item terakhir mulai terlihat.
                        val nearEnd by remember { derivedStateOf {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
                        } }
                        LaunchedEffect(nearEnd, s.data.size, state.loadingMore, state.loadMoreFailed) { if (nearEnd && !state.loadMoreFailed) vm.loadMore() }
                        // key stabil + contentType: kunci scroll halus & reuse komposisi di daftar panjang.
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(s.data, key = { it.id }, contentType = { "video" }) { v -> VideoItem(v, onVideoClick, onChannelClick, libVm) }
                            when {
                                state.loadingMore -> items(2, key = { "more-shimmer-$it" }, contentType = { "shimmer" }) { VideoCardShimmer() }
                                state.loadMoreFailed -> item(key = "more-error", contentType = "footer") { ErrorBox("Gagal memuat video berikutnya.") { vm.loadMore() } }
                                state.endReached -> item(key = "end", contentType = "footer") {
                                    Text("Kamu sudah melihat semua video untuk saat ini.", color = YgMuted, textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
