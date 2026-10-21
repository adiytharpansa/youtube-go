package com.app.youtubego.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.app.youtubego.domain.model.ChannelResult
import com.app.youtubego.presentation.common.EmptyState
import com.app.youtubego.presentation.common.ErrorBox
import com.app.youtubego.presentation.common.VideoCardShimmer
import com.app.youtubego.presentation.library.LibraryViewModel
import com.app.youtubego.presentation.library.VideoItem
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgOutline
import com.app.youtubego.presentation.theme.YgSurface
import com.app.youtubego.presentation.theme.YgSurfaceVariant

/** Halaman maksimum untuk pengisian otomatis saat filter membuat daftar nyaris kosong (scroll manual tetap tak terbatas). */
private const val AUTO_FILL_MAX_PAGE = 8
private const val AUTO_FILL_MIN_ITEMS = 6

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onChannelClick: (String) -> Unit = {},
    vm: SearchViewModel = hiltViewModel(),
    libVm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val recents by vm.recents.collectAsState()
    val subscribed by vm.subscribed.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var filterSheet by remember { mutableStateOf(false) }

    // Buka layar baru: langsung fokus ke kolom cari. Kembali dari pemutar: jangan membuka keyboard lagi.
    LaunchedEffect(Unit) { if (state.phase == SearchPhase.Idle) runCatching { focusRequester.requestFocus() } }

    val hasResults = state.phase != SearchPhase.Idle
    // Saat mengetik ulang di atas hasil, Kembali menutup panel saran dulu (seperti YouTube), baru keluar.
    val closeTyping = { focusManager.clearFocus(); vm.setTyping(false) }
    BackHandler(enabled = state.typing && hasResults) { closeTyping() }

    val search: (String) -> Unit = { q -> focusManager.clearFocus(); vm.submit(q) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (state.typing && hasResults) closeTyping() else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            SearchField(
                query = state.query, onChange = vm::onQueryChange, onClear = { vm.clearQuery(); runCatching { focusRequester.requestFocus() } },
                onSearch = { search(state.query) }, onFocused = { vm.setTyping(true) },
                focusRequester = focusRequester, modifier = Modifier.weight(1f),
            )
        }

        if (hasResults && !state.typing) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "filter") {
                    SearchChip(
                        label = if (state.filters.advancedCount > 0) "Filter (${state.filters.advancedCount})" else "Filter",
                        selected = state.filters.advancedCount > 0, onClick = { filterSheet = true },
                        leading = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                items(SearchType.entries, key = { it.name }) { t ->
                    SearchChip(label = t.label, selected = state.filters.type == t, onClick = { vm.setType(t) })
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.typing || !hasResults -> TypingPanel(
                    query = state.query, suggestions = state.suggestions, recents = recents,
                    onPick = search, onInsert = vm::insertIntoField, onRemove = vm::removeRecent, onClearAll = vm::clearRecents,
                )
                state.phase == SearchPhase.Loading -> LazyColumn(Modifier.fillMaxSize()) { items(4) { VideoCardShimmer() } }
                state.phase == SearchPhase.Error -> EmptyState(
                    icon = Icons.Default.WifiOff, title = "Pencarian gagal", message = state.error,
                    actionLabel = "Coba lagi", onAction = vm::retry,
                )
                else -> ResultsList(state, subscribed, vm, libVm, onVideoClick, onChannelClick)
            }
        }
    }

    if (filterSheet) FilterSheet(state.filters, vm, onDismiss = { filterSheet = false })
}

// ---------------------------------------------------------------------------------------------
// Kolom cari
// ---------------------------------------------------------------------------------------------

@Composable
private fun SearchField(
    query: String, onChange: (String) -> Unit, onClear: () -> Unit, onSearch: () -> Unit, onFocused: () -> Unit,
    focusRequester: FocusRequester, modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(YgSurfaceVariant).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = YgMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query, onValueChange = onChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(YgAccent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { if (it.isFocused) onFocused() },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Cari di YouTube GO : Next", color = YgMuted, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Hapus teks", tint = YgMuted, modifier = Modifier.size(20.dp)) }
        } else Spacer(Modifier.width(14.dp))
    }
}

@Composable
private fun SearchChip(label: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = leading,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = YgAccent.copy(alpha = 0.18f),
            labelColor = YgMuted, selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = YgMuted, selectedLeadingIconColor = YgAccent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true, selected = selected, borderColor = YgOutline, selectedBorderColor = YgAccent.copy(alpha = 0.55f),
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Panel mengetik: riwayat (kolom kosong) atau saran + riwayat yang cocok
// ---------------------------------------------------------------------------------------------

private data class SuggestionEntry(val text: String, val fromHistory: Boolean)

@Composable
private fun TypingPanel(
    query: String, suggestions: List<String>, recents: List<String>,
    onPick: (String) -> Unit, onInsert: (String) -> Unit, onRemove: (String) -> Unit, onClearAll: () -> Unit,
) {
    val q = query.trim()
    if (q.isEmpty()) {
        if (recents.isEmpty()) {
            EmptyState(icon = Icons.Default.Search, title = "Cari apa hari ini?", message = "Ketik kata kunci untuk mencari video, Shorts, dan channel.")
            return
        }
        LazyColumn(Modifier.fillMaxSize().imePadding()) {
            item(key = "recent-header") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Pencarian terbaru", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearAll) { Text("Hapus semua", color = YgAccent) }
                }
            }
            items(recents, key = { "r-$it" }) { r -> SuggestionRow(SuggestionEntry(r, true), onPick, onInsert, onRemove) }
        }
        return
    }
    // Riwayat yang cocok tampil lebih dulu, lalu saran jaringan tanpa duplikat.
    val rows = remember(q, suggestions, recents) {
        val matches = recents.filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) }.take(4).map { SuggestionEntry(it, true) }
        val seen = matches.mapTo(HashSet()) { it.text.lowercase() }
        val net = suggestions.filter { seen.add(it.lowercase()) }.map { SuggestionEntry(it, false) }
        (matches + net).take(10)
    }
    LazyColumn(Modifier.fillMaxSize().imePadding()) {
        // Baris pertama selalu "cari persis apa yang diketik", jadi tetap berguna saat offline / saran belum datang.
        item(key = "literal") { SuggestionRow(SuggestionEntry(q, false), onPick, onInsert, null) }
        items(rows, key = { (if (it.fromHistory) "h-" else "s-") + it.text }) { r -> SuggestionRow(r, onPick, onInsert, if (r.fromHistory) onRemove else null) }
    }
}

@Composable
private fun SuggestionRow(row: SuggestionEntry, onPick: (String) -> Unit, onInsert: (String) -> Unit, onRemove: ((String) -> Unit)?) {
    ListItem(
        headlineContent = { Text(row.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(if (row.fromHistory) Icons.Default.History else Icons.Default.Search, contentDescription = null, tint = YgMuted) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRemove != null) IconButton(onClick = { onRemove(row.text) }) {
                    Icon(Icons.Default.Close, contentDescription = "Hapus dari riwayat", tint = YgMuted, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onInsert(row.text) }) {
                    // Panah serong kiri-atas = "tulis ke kolom cari" (CallMade diputar -90 derajat).
                    Icon(Icons.AutoMirrored.Filled.CallMade, contentDescription = "Isi ke kolom cari", tint = YgMuted, modifier = Modifier.size(20.dp).rotate(-90f))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onPick(row.text) },
    )
}

// ---------------------------------------------------------------------------------------------
// Hasil
// ---------------------------------------------------------------------------------------------

@Composable
private fun ResultsList(
    state: SearchUiState, subscribed: Set<String>, vm: SearchViewModel, libVm: LibraryViewModel,
    onVideoClick: (String) -> Unit, onChannelClick: (String) -> Unit,
) {
    val filters = state.filters
    val visible = remember(state.videos, filters) { filters.apply(state.videos, System.currentTimeMillis() / 1000) }
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    // Kueri atau filter baru: kembali ke atas daftar.
    LaunchedEffect(state.submittedQuery, filters) { listState.scrollToItem(0) }
    // Muat halaman berikutnya saat mendekati ujung, atau saat filter menyisakan terlalu sedikit item.
    val needFill = visible.size < AUTO_FILL_MIN_ITEMS && state.page < AUTO_FILL_MAX_PAGE
    LaunchedEffect(nearEnd, needFill, state.page, state.loadingMore, state.loadMoreFailed, state.endReached) {
        if ((nearEnd || needFill) && !state.loadMoreFailed) vm.loadMore()
    }

    val showChannels = filters.type == SearchType.ALL && filters.advancedCount == 0
    val channels = if (showChannels) state.channels else emptyList()
    val settled = !state.loadingMore && (state.endReached || state.page >= AUTO_FILL_MAX_PAGE)

    if (visible.isEmpty() && channels.isEmpty() && settled && !state.loadMoreFailed) {
        val filtered = filters != SearchFilters()
        EmptyState(
            icon = Icons.Default.SearchOff,
            title = "Tidak ada hasil",
            message = if (filtered) "Tidak ada yang cocok dengan filter ini. Coba longgarkan filter." else "Tidak ada hasil untuk \"${state.submittedQuery}\". Periksa ejaan atau coba kata kunci lain.",
            actionLabel = if (filtered) "Atur ulang filter" else null,
            onAction = vm::resetFilters,
        )
        return
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(channels, key = { "ch-${it.id}" }, contentType = { "channel" }) { ch ->
            ChannelResultRow(ch, ch.id in subscribed, onOpen = onChannelClick, onToggle = vm::toggleSubscribe)
        }
        items(visible, key = { it.id }, contentType = { "video" }) { v -> VideoItem(v, onVideoClick, onChannelClick, libVm) }
        when {
            state.loadingMore || (visible.isEmpty() && !settled && !state.loadMoreFailed) ->
                items(2, key = { "more-shimmer-$it" }, contentType = { "shimmer" }) { VideoCardShimmer() }
            state.loadMoreFailed -> item(key = "more-error", contentType = "footer") { ErrorBox("Gagal memuat hasil berikutnya.") { vm.retryLoadMore() } }
            state.endReached -> item(key = "end", contentType = "footer") {
                Text("Itu semua hasil yang tersedia.", color = YgMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
            }
        }
    }
}

@Composable
private fun ChannelResultRow(ch: ChannelResult, subscribed: Boolean, onOpen: (String) -> Unit, onToggle: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpen(ch.id) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = ch.avatarUrl, contentDescription = "Channel ${ch.name}", contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(YgSurfaceVariant),
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(ch.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOf(ch.id.takeIf { it.startsWith("@") }, ch.subscriberText.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" • ")
            if (meta.isNotEmpty()) Text(meta, color = YgMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (subscribed) FilledTonalButton(onClick = { onToggle(ch.id) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp)); Text(" Terlangganan")
        } else Button(
            onClick = { onToggle(ch.id) }, contentPadding = PaddingValues(horizontal = 14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background),
        ) { Text("Berlangganan", fontWeight = FontWeight.SemiBold) }
    }
}

// ---------------------------------------------------------------------------------------------
// Lembar filter
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(filters: SearchFilters, vm: SearchViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = YgSurface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filter pencarian", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::resetFilters, enabled = filters != SearchFilters()) { Text("Atur ulang") }
            }
            FilterGroup("Waktu upload", UploadDate.entries, filters.date, { it.label }, vm::setDate)
            FilterGroup("Durasi", DurationFilter.entries, filters.duration, { it.label }, vm::setDuration)
            FilterGroup("Urutkan berdasarkan", SortBy.entries, filters.sort, { it.label }, vm::setSort)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = YgAccent, contentColor = Color.White)) { Text("Selesai") }
        }
    }
}

@Composable
private fun <T> FilterGroup(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Text(title, color = YgMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> SearchChip(label = label(o), selected = o == selected, onClick = { onSelect(o) }) }
    }
}
