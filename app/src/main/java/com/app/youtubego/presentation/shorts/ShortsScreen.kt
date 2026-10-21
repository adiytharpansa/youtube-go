package com.app.youtubego.presentation.shorts

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.data.newpipe.DeviceStreamSource
import com.app.youtubego.data.newpipe.DevicePlayback
import com.app.youtubego.domain.model.CommentItem
import com.app.youtubego.domain.model.StreamOption
import com.app.youtubego.domain.model.TopicCatalog
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.domain.usecase.toFriendlyMessage
import com.app.youtubego.playback.PlayerHolder
import com.app.youtubego.presentation.common.EmptyState
import com.app.youtubego.presentation.common.UiState
import com.app.youtubego.presentation.common.formatDuration
import com.app.youtubego.presentation.common.formatViewsId
import com.app.youtubego.presentation.player.CommentsSheet
import com.app.youtubego.presentation.theme.ForceLightStatusBarIcons
import com.app.youtubego.presentation.theme.YgAccent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.roundToInt

data class ShortsUiState(
    val videos: List<VideoSummary> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

/** Sumber putar satu Short: stream gabungan (video+audio jadi satu) atau video & audio terpisah (kualitas lebih tinggi). */
sealed interface ShortSource {
    data class Progressive(val url: String) : ShortSource
    data class Adaptive(val videoUrl: String, val audioUrl: String) : ShortSource
}

private fun qualityOf(o: StreamOption): Int = o.qualityLabel.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

/**
 * Urutan prioritas: video-saja + audio-saja (sampai [maxQ]p, bila lebih tinggi dari stream gabungan terbaik),
 * lalu stream gabungan terbaik. Bila yang pertama gagal diputar, pemutar otomatis turun ke berikutnya.
 */
private fun buildSources(dp: DevicePlayback, maxQ: Int): List<ShortSource> {
    val out = ArrayList<ShortSource>()
    val vids = dp.videoOnly.filter { it.url.isNotBlank() && qualityOf(it) in 1..maxQ }
    val bestQ = vids.maxOfOrNull { qualityOf(it) }
    val video = vids.filter { qualityOf(it) == bestQ }.let { c -> c.firstOrNull { it.mimeType.contains("mp4") } ?: c.firstOrNull() }
    val audios = dp.audioOnly.filter { it.url.isNotBlank() }
    val audio = audios.lastOrNull { it.mimeType.contains("mp4") } ?: audios.lastOrNull()
    val muxedBest = dp.combined.lastOrNull { it.url.isNotBlank() }
    if (video != null && audio != null && qualityOf(video) > (muxedBest?.let { qualityOf(it) } ?: 0)) {
        out += ShortSource.Adaptive(video.url, audio.url)
    }
    muxedBest?.let { out += ShortSource.Progressive(it.url) }
    return out
}

/**
 * Shorts: video pendek vertikal, geser atas/bawah. Sumber = pencarian `duration=short` berputar di sejumlah topik,
 * disaring ke durasi <= 3 menit; halaman tak terbatas lewat putaran kueri + nomor halaman.
 *
 * Kolam pemutar: Short berikutnya sudah disiapkan (prepare) di ExoPlayer cadangan saat yang sekarang diputar,
 * dan Short yang baru ditinggalkan disimpan sebentar, sehingga geser maju/mundur langsung jalan tanpa menunggu.
 * Maksimum 2 pemutar cadangan (+1 aktif) supaya decoder perangkat murah tidak kehabisan.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val repo: VideoRepository,
    private val deviceSource: DeviceStreamSource,
    private val library: UserLibraryStore,
    @ApplicationContext private val appContext: Context,
    holder: PlayerHolder,
) : ViewModel() {
    /** Sumber data dengan header per-klien YouTube (sama dengan pemutar utama): tanpa ini stream ANDROID/IOS/VISIONOS ditolak 403. */
    val dataSourceFactory = holder.dataSourceFactory
    private val _state = MutableStateFlow(ShortsUiState())
    val state: StateFlow<ShortsUiState> = _state.asStateFlow()
    val liked: StateFlow<Set<String>> = library.liked
    val saved: StateFlow<Set<String>> = library.saved
    val subscribed: StateFlow<Set<String>> = library.subscribed

    private val _likeCounts = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Jumlah suka per Short (dari ekstraksi perangkat); tak ada entri = belum/tidak diketahui. */
    val likeCounts: StateFlow<Map<String, Long>> = _likeCounts.asStateFlow()

    private val queries = listOf("shorts", "#shorts viral", "shorts lucu", "shorts musik") + TopicCatalog.all.map { "#shorts ${it.label}" }
    private var cursor = 0
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel(); cursor = 0
        _state.value = ShortsUiState(loading = true)
        job = viewModelScope.launch { fetch(initial = true) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.videos.isEmpty()) return
        _state.update { it.copy(loadingMore = true) }
        job = viewModelScope.launch { fetch(initial = false) }
    }

    /**
     * Ambil kueri berikutnya sampai ada Short baru. Penyebab "Shorts belum bisa dimuat" sebelumnya: hasil pencarian
     * kebanyakan video panjang, sehingga saringan durasi membuang semuanya. Sekarang: 2 kueri paralel per putaran
     * (maks. 5 putaran), saringan <= 3 menit, dan bila tetap kosong pada muat pertama, pakai video terpendek
     * dari hasil yang ada supaya layar Shorts selalu menampilkan sesuatu.
     */
    private suspend fun fetch(initial: Boolean) {
        val known = _state.value.videos.mapTo(HashSet()) { it.id }
        var fresh: List<VideoSummary> = emptyList()
        val pool = LinkedHashMap<String, VideoSummary>()
        var lastError: Throwable? = null
        var rounds = 0
        while (fresh.isEmpty() && rounds < 5) {
            rounds++
            val batch = List(2) {
                val q = queries[cursor % queries.size]
                val page = cursor / queries.size + 1
                cursor++
                q to page
            }
            val results = coroutineScope {
                batch.map { (q, p) -> async { repo.search(q, page = p, shortsOnly = false) } }.awaitAll()
            }
            val shorts = ArrayList<VideoSummary>()
            results.forEach { r ->
                r.fold(
                    onSuccess = { list ->
                        list.forEach { v ->
                            if (v.isLive || v.lengthSeconds <= 0 || v.id in known) return@forEach
                            if (v.lengthSeconds <= 180) { if (known.add(v.id)) shorts += v }
                            else if (v.lengthSeconds <= 600) pool.putIfAbsent(v.id, v)
                        }
                    },
                    onFailure = { lastError = it },
                )
            }
            fresh = shorts
        }
        // Cadangan: tak ada video <= 3 menit sama sekali -> tampilkan yang terpendek (<= 10 menit).
        if (fresh.isEmpty() && _state.value.videos.isEmpty() && pool.isNotEmpty()) {
            fresh = pool.values.sortedBy { it.lengthSeconds }.take(20).filter { known.add(it.id) }
        }
        _state.update { cur ->
            if (fresh.isEmpty() && cur.videos.isEmpty()) cur.copy(loading = false, loadingMore = false, error = (lastError ?: IllegalStateException("kosong")).toFriendlyMessage())
            else cur.copy(videos = cur.videos + fresh, loading = false, loadingMore = false, error = null)
        }
    }

    /**
     * Pra-ekstrak Short berikutnya selagi yang sekarang diputar: hasilnya masuk cache DeviceStreamSource,
     * jadi saat digeser, video langsung mulai (tanpa menunggu 1-3 dtk ekstraksi tiap geseran).
     */
    private val prefetched = HashSet<String>()
    fun prefetch(videos: List<VideoSummary>) {
        videos.forEach { v ->
            if (prefetched.add(v.id)) viewModelScope.launch { withTimeoutOrNull(10_000) { deviceSource.resolve(v.id) } }
        }
    }

    /** Daftar sumber putar untuk satu Short, terbaik dulu: jalur perangkat (video+audio terpisah, lalu gabungan), lalu API server. */
    suspend fun resolveSources(id: String, force: Boolean = false): List<ShortSource> {
        val dp = withTimeoutOrNull(10_000) { deviceSource.resolve(id, force).getOrNull() }
        if (dp != null && dp.likeCount > 0) _likeCounts.update { it + (id to dp.likeCount) }
        val list = dp?.let { buildSources(it, if (library.highQuality.value) 1080 else 720) }.orEmpty()
        return list.ifEmpty { listOfNotNull(apiSource(id)) }
    }

    /** Cadangan terakhir bila semua sumber perangkat gagal diputar: stream gabungan dari API server. */
    suspend fun apiSource(id: String): ShortSource? =
        repo.videoDetail(id).getOrNull()?.combinedStreams?.lastOrNull { it.url.isNotBlank() }?.let { ShortSource.Progressive(it.url) }

    suspend fun comments(id: String): Result<List<CommentItem>> = repo.comments(id)

    // ---- Kolam pemutar ----
    private val pool = LinkedHashMap<String, ExoPlayer>()
    private val inUse = HashSet<String>()
    private val preloading = HashSet<String>()
    @Volatile private var screenVisible = true

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        // Mulai putar setelah 400 ms terisi (bawaan 2,5 dtk): Short terasa langsung jalan saat digeser.
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(3_000, 12_000, 400, 800).setPrioritizeTimeOverSizeThresholds(true).build()
        )
        // Fokus audio: Short mulai -> pemutar video utama/notifikasi dijeda otomatis (tak tumpang tindih).
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .build().apply { repeatMode = Player.REPEAT_MODE_ONE; playWhenReady = false }

    private fun mediaSourceOf(s: ShortSource): MediaSource {
        val factory = ProgressiveMediaSource.Factory(dataSourceFactory)
        return when (s) {
            is ShortSource.Progressive -> factory.createMediaSource(MediaItem.fromUri(s.url))
            is ShortSource.Adaptive -> MergingMediaSource(
                factory.createMediaSource(MediaItem.fromUri(s.videoUrl)),
                factory.createMediaSource(MediaItem.fromUri(s.audioUrl)),
            )
        }
    }

    private fun trimPool() {
        while (pool.size > 2) { val k = pool.keys.first(); pool.remove(k)?.release() }
    }

    /** Siapkan pemutar cadangan (tanpa memutar) untuk Short yang akan datang. */
    fun preload(videos: List<VideoSummary>) {
        videos.forEach { v ->
            val id = v.id
            if (id in pool || id in inUse || !preloading.add(id)) return@forEach
            viewModelScope.launch {
                try {
                    val srcs = resolveSources(id)
                    if (!screenVisible || srcs.isEmpty() || id in pool || id in inUse) return@launch
                    val p = newPlayer()
                    p.setMediaSource(mediaSourceOf(srcs.first())); p.prepare()
                    pool.remove(id)?.release()
                    pool[id] = p
                    trimPool()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // preload hanya optimasi: gagal = abaikan, pemutar dibuat ulang saat Short dibuka
                } finally { preloading.remove(id) }
            }
        }
    }

    /** Ambil pemutar untuk Short aktif: dari kolam bila sudah siap (hanya percobaan pertama), selain itu buat baru. */
    fun acquire(id: String, source: ShortSource, allowPooled: Boolean): ExoPlayer {
        inUse.add(id)
        val pooled = pool.remove(id)
        if (pooled != null && allowPooled && pooled.playerError == null) { pooled.playWhenReady = true; return pooled }
        pooled?.release()
        return newPlayer().also { it.setMediaSource(mediaSourceOf(source)); it.prepare(); it.playWhenReady = true }
    }

    /** Short ditinggalkan: pemutar sehat dijeda & disimpan ke kolam (geser balik langsung jalan); yang galat/layar tertutup dilepas. */
    fun recycle(id: String, exo: ExoPlayer, healthy: Boolean) {
        inUse.remove(id)
        if (!healthy || !screenVisible) { exo.release(); return }
        exo.pause(); exo.seekTo(0)
        pool.remove(id)?.release()
        pool[id] = exo
        trimPool()
    }

    fun onScreenVisible(visible: Boolean) {
        screenVisible = visible
        if (!visible) { pool.values.forEach { it.release() }; pool.clear(); preloading.clear() }
    }

    override fun onCleared() {
        pool.values.forEach { it.release() }; pool.clear()
        super.onCleared()
    }

    fun onWatched(v: VideoSummary) = library.addHistory(v)
    fun toggleLike(v: VideoSummary) = library.toggleLiked(v)
    fun toggleSave(v: VideoSummary) = library.toggleSaved(v)
    fun toggleSubscribe(channelId: String) = library.toggleSubscribed(channelId)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(onChannelClick: (String) -> Unit, vm: ShortsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val liked by vm.liked.collectAsState()
    val saved by vm.saved.collectAsState()
    val subscribed by vm.subscribed.collectAsState()
    val likeCounts by vm.likeCounts.collectAsState()

    // Layar ditinggalkan -> semua pemutar cadangan dilepas (hemat memori & decoder).
    DisposableEffect(vm) {
        vm.onScreenVisible(true)
        onDispose { vm.onScreenVisible(false) }
    }

    when {
        state.loading -> {
            ForceLightStatusBarIcons()
            ShortsSkeleton()
        }
        state.videos.isEmpty() -> EmptyState(
            Icons.Outlined.SlowMotionVideo, "Shorts belum bisa dimuat", state.error ?: "Coba lagi sebentar.",
            actionLabel = "Coba lagi", onAction = vm::load,
        )
        else -> {
            ForceLightStatusBarIcons()
            val pager = rememberPagerState { state.videos.size }
            // Muat halaman berikutnya saat tersisa 3 video lagi.
            LaunchedEffect(pager.currentPage, state.videos.size) {
                if (pager.currentPage >= state.videos.size - 3) vm.loadMore()
            }
            LaunchedEffect(pager.currentPage, state.videos.size) {
                vm.prefetch(state.videos.drop(pager.currentPage + 1).take(3))
                vm.preload(state.videos.drop(pager.currentPage + 1).take(1))
            }
            VerticalPager(state = pager, modifier = Modifier.fillMaxSize().background(Color.Black), beyondViewportPageCount = 0, key = { state.videos[it].id }) { i ->
                val v = state.videos[i]
                ShortPage(
                    video = v, active = pager.currentPage == i, vm = vm,
                    liked = v.id in liked, saved = v.id in saved, subscribed = v.authorId in subscribed,
                    likeCount = likeCounts[v.id] ?: 0L,
                    onChannelClick = onChannelClick,
                )
            }
        }
    }
}

private data class Burst(val id: Int, val pos: Offset)

private val HASHTAG = Regex("""#[\p{L}\p{N}_]+""")

/** Hashtag diberi warna tautan; sisanya apa adanya. */
private fun hashtagText(s: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    HASHTAG.findAll(s).forEach { m ->
        append(s.substring(last, m.range.first))
        pushStyle(SpanStyle(color = Color(0xFF8CB8FF), fontWeight = FontWeight.Medium))
        append(m.value)
        pop()
        last = m.range.last + 1
    }
    append(s.substring(last))
}

private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f)

@Composable
private fun ShortPage(
    video: VideoSummary, active: Boolean, vm: ShortsViewModel,
    liked: Boolean, saved: Boolean, subscribed: Boolean, likeCount: Long, onChannelClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var ready by remember(video.id, active) { mutableStateOf(false) }
    var expanded by remember(video.id) { mutableStateOf(false) }
    var showComments by remember(video.id) { mutableStateOf(false) }
    var burst by remember { mutableStateOf<Burst?>(null) }
    var burstSeq by remember { mutableIntStateOf(0) }
    val posterAlpha by animateFloatAsState(if (ready) 0f else 1f, tween(220), label = "poster")
    val shade by animateFloatAsState(if (expanded) 0.9f else 0.72f, tween(200), label = "shade")
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Poster: tampil sampai bingkai pertama video siap (lalu memudar), dan satu-satunya isi untuk halaman tak aktif (hemat memori & data).
        AsyncImage(video.thumbnailUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = posterAlpha })
        if (active) ShortPlayer(
            video = video, vm = vm,
            onFirstFrame = { ready = true },
            onDoubleTap = { pos ->
                if (!liked) vm.toggleLike(video)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                burstSeq += 1
                burst = Burst(burstSeq, pos)
            },
        )

        // Gradasi atas: ikon status bar tetap terbaca di atas video terang (video kini sampai ke bawah status bar).
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(statusTop + 36.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(0.35f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(240.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(shade)))))

        ShortCaption(
            video = video, subscribed = subscribed, expanded = expanded, onToggleExpanded = { expanded = !expanded },
            onChannelClick = onChannelClick, onToggleSubscribe = { vm.toggleSubscribe(video.authorId) },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 84.dp, bottom = 36.dp),
        )

        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val likeLabel = if (likeCount > 0) formatViewsId(likeCount + if (liked) 1 else 0) else "Suka"
            RailButton(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, likeLabel, liked, popOnActive = true) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.toggleLike(video)
            }
            RailButton(Icons.Outlined.ChatBubbleOutline, "Komentar", false) { showComments = true }
            RailButton(if (saved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater, if (saved) "Tersimpan" else "Simpan", saved, popOnActive = true) { vm.toggleSave(video) }
            RailButton(Icons.Filled.Share, "Bagikan", false) {
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${video.title}\nhttps://youtube.com/shorts/${video.id}") }
                context.startActivity(Intent.createChooser(send, "Bagikan Short"))
            }
        }

        burst?.let { b -> HeartBurst(b) { if (burst?.id == b.id) burst = null } }
        if (showComments) ShortComments(video.id, vm) { showComments = false }
    }
}

@Composable
private fun ShortCaption(
    video: VideoSummary, subscribed: Boolean, expanded: Boolean, onToggleExpanded: () -> Unit,
    onChannelClick: (String) -> Unit, onToggleSubscribe: () -> Unit, modifier: Modifier = Modifier,
) {
    val noIndication = remember { MutableInteractionSource() }
    val title = remember(video.title) { hashtagText(video.title) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                video.authorAvatarUrl, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(0.15f))
                    .border(1.dp, Color.White.copy(0.55f), CircleShape)
                    .clickable(enabled = video.authorId.isNotBlank(), role = Role.Button) { onChannelClick(video.authorId) },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                video.author, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(shadow = TextShadow),
                modifier = Modifier.weight(1f, fill = false).clickable(enabled = video.authorId.isNotBlank()) { onChannelClick(video.authorId) },
            )
            if (video.authorId.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                // Area sentuh 48dp, pil tampil 32dp.
                Box(Modifier.defaultMinSize(minHeight = 48.dp).clickable(role = Role.Button, onClick = onToggleSubscribe), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (subscribed) Color.White.copy(0.22f) else Color.White).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (subscribed) "Diikuti" else "Ikuti", color = if (subscribed) Color.White else Color.Black, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Text(
            title, color = Color.White, maxLines = if (expanded) 8 else 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = TextShadow),
            modifier = Modifier.padding(top = 2.dp).animateContentSize().clickable(interactionSource = noIndication, indication = null, onClick = onToggleExpanded),
        )
        if (video.viewCount > 0) Text(
            "${formatViewsId(video.viewCount)} tontonan", color = Color.White.copy(0.75f),
            style = MaterialTheme.typography.labelMedium.copy(shadow = TextShadow), modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Tombol rail: lingkaran 48dp, tekan = mengecil dengan pegas, aktif = "pop". Label di bawah dengan bayangan agar terbaca. */
@Composable
private fun RailButton(icon: ImageVector, label: String, active: Boolean, popOnActive: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.86f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "railPress",
    )
    val pop = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (popOnActive && active && !previous) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        previous = active
    }
    Column(
        Modifier.widthIn(min = 56.dp)
            .graphicsLayer { scaleX = pressScale * pop.value; scaleY = pressScale * pop.value }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(0.42f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (active) YgAccent else Color.White)
        }
        Text(
            label, color = Color.White, maxLines = 1, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(shadow = TextShadow), modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** Hati besar muncul di titik ketukan ganda: membesar dengan pegas lalu memudar. */
@Composable
private fun HeartBurst(burst: Burst, onDone: () -> Unit) {
    val scale = remember(burst.id) { Animatable(0.4f) }
    val fade = remember(burst.id) { Animatable(1f) }
    LaunchedEffect(burst.id) {
        launch { scale.animateTo(1.15f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        delay(450)
        fade.animateTo(0f, tween(250))
        onDone()
    }
    val half = with(LocalDensity.current) { 48.dp.toPx() }
    Icon(
        Icons.Filled.Favorite, contentDescription = null, tint = YgAccent,
        modifier = Modifier
            .offset { IntOffset((burst.pos.x - half).roundToInt(), (burst.pos.y - half).roundToInt()) }
            .size(96.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = fade.value },
    )
}

/** Panel komentar (memakai CommentsSheet milik pemutar utama). */
@Composable
private fun ShortComments(videoId: String, vm: ShortsViewModel, onDismiss: () -> Unit) {
    val state by produceState<UiState<List<CommentItem>>>(UiState.Loading, videoId) {
        value = vm.comments(videoId).fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it.toFriendlyMessage()) },
        )
    }
    CommentsSheet(state, onSeek = {}, onDismiss = onDismiss)
}

/**
 * Pemutar milik satu Short aktif. Urutan: ekstrak sumber -> pemutar (dari kolam bila sudah disiapkan) -> bila gagal,
 * turun ke sumber berikutnya (video+audio terpisah -> gabungan -> API server) sebelum menyerah.
 */
@Composable
private fun ShortPlayer(video: VideoSummary, vm: ShortsViewModel, onFirstFrame: () -> Unit, onDoubleTap: (Offset) -> Unit) {
    val scope = rememberCoroutineScope()
    var retry by remember(video.id) { mutableIntStateOf(0) }
    var attempt by remember(video.id) { mutableIntStateOf(0) }
    var apiSource by remember(video.id) { mutableStateOf<ShortSource?>(null) }
    var apiTried by remember(video.id) { mutableStateOf(false) }
    var failed by remember(video.id) { mutableStateOf(false) }
    val resolved by produceState<List<ShortSource>?>(null, video.id, retry) {
        value = null
        value = vm.resolveSources(video.id, force = retry > 0)
    }
    LaunchedEffect(video.id) { vm.onWatched(video) }

    val list = resolved
    val all = remember(list, apiSource) { list.orEmpty() + listOfNotNull(apiSource) }
    val source = all.getOrNull(attempt)
    when {
        list == null -> Box(Modifier.fillMaxSize()) {
            BufferingSweep(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
        }
        failed || source == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Short ini tidak bisa diputar.", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(shadow = TextShadow))
                TextButton(onClick = { retry += 1; attempt = 0; apiTried = false; apiSource = null; failed = false }) {
                    Text("Coba lagi", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                Text("atau geser ke berikutnya", color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelMedium.copy(shadow = TextShadow))
            }
        }
        else -> key(video.id, attempt, retry) {
            ShortPlayerContent(
                video = video, source = source, firstAttempt = attempt == 0, vm = vm,
                onFirstFrame = onFirstFrame, onDoubleTap = onDoubleTap,
                onError = {
                    if (attempt + 1 < all.size) attempt += 1
                    else if (!apiTried) {
                        apiTried = true
                        scope.launch {
                            val s = vm.apiSource(video.id)
                            if (s != null && all.none { it == s }) { apiSource = s; attempt += 1 } else failed = true
                        }
                    } else failed = true
                },
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ShortPlayerContent(
    video: VideoSummary, source: ShortSource, firstAttempt: Boolean, vm: ShortsViewModel,
    onFirstFrame: () -> Unit, onDoubleTap: (Offset) -> Unit, onError: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val exo = remember { vm.acquire(video.id, source, firstAttempt) }
    val viewHolder = remember { arrayOfNulls<PlayerView>(1) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var aspect by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val latestFirstFrame by rememberUpdatedState(onFirstFrame)
    val latestError by rememberUpdatedState(onError)
    val latestDoubleTap by rememberUpdatedState(onDoubleTap)

    DisposableEffect(exo, lifecycleOwner) {
        var hadError = false
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true; latestFirstFrame() }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) { buffering = playbackState == Player.STATE_BUFFERING }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
            override fun onPlayerError(error: PlaybackException) { hadError = true; latestError() }
        }
        exo.addListener(listener)
        // Pemutar dari kolam mungkin sudah siap sebelum pendengar terpasang: sinkronkan keadaan awal.
        playing = exo.isPlaying
        buffering = exo.playbackState == Player.STATE_BUFFERING
        val vs = exo.videoSize
        if (vs.width > 0 && vs.height > 0) aspect = vs.width * vs.pixelWidthHeightRatio / vs.height
        // Lanjut hanya bila tadinya memang berputar: Short yang sengaja dijeda tidak menyala sendiri.
        var wasPlaying = false
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) { wasPlaying = exo.playWhenReady; exo.pause() }
            else if (e == Lifecycle.Event.ON_RESUME && wasPlaying) exo.play()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            exo.removeListener(listener)
            viewHolder[0]?.player = null
            vm.recycle(video.id, exo, healthy = !hadError)
        }
    }
    LaunchedEffect(exo) {
        while (true) {
            val d = exo.duration
            durationMs = if (d > 0) d else 0L
            if (!scrubbing) progress = if (d > 0) (exo.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(80)
        }
    }

    var resumeAfterScrub by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().pointerInput(exo) {
            detectTapGestures(
                onTap = { if (exo.isPlaying) exo.pause() else exo.play() },
                onDoubleTap = { latestDoubleTap(it) },
            )
        },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.player = exo
                    viewHolder[0] = this
                }
            },
            // Video vertikal memenuhi layar (zoom, tanpa pita hitam); video lebar tetap utuh (fit).
            update = { v -> v.resizeMode = if (aspect > 0.1f && aspect < 0.75f) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
        )

        AnimatedVisibility(
            visible = ready && !playing && !buffering && !scrubbing,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.8f, animationSpec = tween(160)),
            exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.9f, animationSpec = tween(100)),
        ) {
            Box(Modifier.size(76.dp).clip(CircleShape).background(Color.Black.copy(0.35f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Lanjutkan", tint = Color.White, modifier = Modifier.size(52.dp))
            }
        }

        AnimatedVisibility(
            visible = scrubbing, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            enter = fadeIn(tween(100)), exit = fadeOut(tween(160)),
        ) {
            Text(
                "${formatDuration((scrubFraction * durationMs / 1000f).toLong())} / ${formatDuration(durationMs / 1000)}",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                style = MaterialTheme.typography.titleLarge.copy(shadow = TextShadow),
            )
        }

        ShortSeekBar(
            progress = if (scrubbing) scrubFraction else progress, dragging = scrubbing, buffering = buffering,
            onDragStart = { f ->
                resumeAfterScrub = exo.isPlaying
                exo.pause(); scrubbing = true; scrubFraction = f
            },
            onDrag = { f ->
                scrubFraction = f
                if (durationMs > 0) exo.seekTo((f * durationMs).toLong())
            },
            onDragEnd = { f ->
                if (f != null && durationMs > 0) exo.seekTo((f * durationMs).toLong())
                scrubbing = false
                if (resumeAfterScrub) exo.play()
            },
            onTapSeek = { f -> if (durationMs > 0) { exo.seekTo((f * durationMs).toLong()); progress = f } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Bilah progres: 2dp saat diam; digeser = 6dp + pegangan + pratinjau waktu. Ketuk = loncat. Gelombang bergerak saat buffering. */
@Composable
private fun ShortSeekBar(
    progress: Float, dragging: Boolean, buffering: Boolean,
    onDragStart: (Float) -> Unit, onDrag: (Float) -> Unit, onDragEnd: (Float?) -> Unit, onTapSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val density = LocalDensity.current
    val barH by animateDpAsState(if (dragging) 6.dp else 2.dp, tween(120), label = "barH")
    val thumb by animateFloatAsState(if (dragging) 1f else 0f, tween(120), label = "thumb")
    val onStart by rememberUpdatedState(onDragStart)
    val onMove by rememberUpdatedState(onDrag)
    val onEnd by rememberUpdatedState(onDragEnd)
    val onTap by rememberUpdatedState(onTapSeek)
    Box(
        modifier.fillMaxWidth().height(32.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures(onTap = { off -> onTap((off.x / widthPx).coerceIn(0f, 1f)) }) }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { off -> last = (off.x / widthPx).coerceIn(0f, 1f); onStart(last) },
                    onHorizontalDrag = { change, _ -> change.consume(); last = (change.position.x / widthPx).coerceIn(0f, 1f); onMove(last) },
                    onDragEnd = { onEnd(last) },
                    onDragCancel = { onEnd(null) },
                )
            },
    ) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(barH).background(Color.White.copy(0.25f)))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(barH).background(YgAccent))
        if (buffering && !dragging) BufferingSweep(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(barH))
        Box(
            Modifier.align(Alignment.BottomStart)
                .offset(x = with(density) { (widthPx * progress.coerceIn(0f, 1f)).toDp() } - 7.dp, y = 7.dp - barH / 2)
                .size(14.dp).graphicsLayer { scaleX = thumb; scaleY = thumb }.clip(CircleShape).background(Color.White),
        )
    }
}

/** Sorot putih yang meluncur di sepanjang bilah: tanda "sedang memuat" yang halus (pengganti spinner). */
@Composable
private fun BufferingSweep(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sweep")
    val x by transition.animateFloat(-0.4f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "x")
    Canvas(modifier.clipToBounds()) {
        drawRect(Color.White.copy(alpha = 0.75f), topLeft = Offset(size.width * x, 0f), size = Size(size.width * 0.4f, size.height))
    }
}

@Composable
private fun Modifier.darkShimmer(strong: Boolean = false): Modifier {
    val transition = rememberInfiniteTransition(label = "sk")
    val x = transition.animateFloat(-500f, 1500f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
    val base = if (strong) Color(0xFF1C1C21) else Color(0xFF0E0E11)
    val high = if (strong) Color(0xFF2E2E35) else Color(0xFF1A1A1F)
    return drawBehind {
        val xv = x.value
        drawRect(Brush.linearGradient(listOf(base, high, base), start = Offset(xv, 0f), end = Offset(xv + 500f, 300f)))
    }
}

/** Kerangka pemuatan awal: bentuk caption + rail yang sama dengan halaman asli, jadi tidak ada lompatan tata letak. */
@Composable
private fun ShortsSkeleton() {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().darkShimmer())
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 84.dp, bottom = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).darkShimmer(strong = true))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).darkShimmer(strong = true))
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(7.dp)).darkShimmer(strong = true))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(7.dp)).darkShimmer(strong = true))
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            repeat(4) { Box(Modifier.size(48.dp).clip(CircleShape).darkShimmer(strong = true)) }
        }
    }
}
