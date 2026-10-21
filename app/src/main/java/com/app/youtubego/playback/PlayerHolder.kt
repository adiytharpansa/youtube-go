package com.app.youtubego.playback

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.domain.model.VideoSummary
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Pemutaran playlist berurutan: nama + daftar video sesuai urutan tampil di layar playlist. */
data class PlaylistRun(val name: String, val videos: List<VideoSummary>)

/**
 * Satu-satunya ExoPlayer aplikasi untuk video penuh (Shorts punya pemutar sendiri yang singkat).
 *
 * Kenapa singleton, bukan dibuat per layar:
 * - Pemutar harus HIDUP di luar siklus Activity/Compose agar suara tetap jalan saat layar mati
 *   atau aplikasi ke latar belakang; [PlaybackService] memegang pemutar yang sama lewat MediaSession.
 * - Berpindah video tidak lagi membuang & membangun ulang decoder/koneksi (penyebab jeda & tersendat).
 *
 * Semua pemanggilan harus dari main thread (default ExoPlayer).
 */
@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: UserLibraryStore,
) {

    val player: ExoPlayer by lazy { create() }

    /** Posisi terakhir per mediaId, agar kembali ke video yang sama melanjutkan, bukan dari nol. */
    private val positions = HashMap<String, Long>()

    /** true = layar pemutar ditutup untuk DIKECILKAN (mini player), bukan dihentikan. Dibaca & direset layar pemutar saat keluar. */
    @Volatile var minimizeOnLeave = false

    /** Video berikutnya (rekomendasi pertama) yang diisi layar pemutar; dipakai tombol "Berikutnya" di notifikasi/layar kunci. */
    @Volatile var upNextId: String? = null
    private val _skipNext = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emisi id video saat pengguna menekan "Berikutnya" dari notifikasi; navigasi aplikasi yang membukanya. */
    val skipNext: SharedFlow<String> = _skipNext.asSharedFlow()
    fun requestNext() { upNextId?.let { _skipNext.tryEmit(it) } }

    // ---- Playlist berurutan: Berikutnya/Sebelumnya mengikuti urutan playlist ----
    private val _run = MutableStateFlow<PlaylistRun?>(null)
    val playlistRun: StateFlow<PlaylistRun?> = _run.asStateFlow()
    fun startPlaylist(name: String, videos: List<VideoSummary>) { _run.value = if (videos.isEmpty()) null else PlaylistRun(name, videos) }
    fun clearPlaylist() { _run.value = null }
    /** Video SEBELUMNYA di playlist yang diisi layar pemutar; dipakai tombol "Sebelumnya" di notifikasi/layar kunci. */
    @Volatile var upPrevId: String? = null
    private val _skipPrev = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val skipPrev: SharedFlow<String> = _skipPrev.asSharedFlow()
    fun requestPrevious() { upPrevId?.let { _skipPrev.tryEmit(it) } }

    // ---- Antrean ("Tambahkan ke antrean"): diputar lebih dulu daripada rekomendasi saat video selesai / Berikutnya ----
    private val _queue = MutableStateFlow<List<VideoSummary>>(emptyList())
    val queue: StateFlow<List<VideoSummary>> = _queue.asStateFlow()
    /** false bila video itu sedang diputar atau sudah ada di antrean. */
    fun enqueue(v: VideoSummary): Boolean {
        if (v.id.isBlank() || v.id == player.currentMediaItem?.mediaId || _queue.value.any { it.id == v.id }) return false
        _queue.value = _queue.value + v
        return true
    }
    fun removeFromQueue(id: String) { if (_queue.value.any { it.id == id }) _queue.value = _queue.value.filter { it.id != id } }

    // ---- Lanjut menonton: simpan posisi tiap 10 dtk saat memutar, saat jeda, dan saat berhenti ----
    private val saveRunnable = object : Runnable {
        override fun run() { persistProgress(); if (player.isPlaying) handler.postDelayed(this, 10_000) }
    }
    private fun persistProgress() {
        val p = player
        val id = p.currentMediaItem?.mediaId ?: return
        val dur = p.duration
        if (dur == C.TIME_UNSET || p.isCurrentMediaItemLive || dur < 60_000L) return
        val pos = p.currentPosition
        if (p.playbackState == Player.STATE_ENDED || pos >= dur - 10_000L) library.saveProgress(id, 0L)
        else if (pos > 5_000L) library.saveProgress(id, pos)
    }

    // ---- Timer tidur: jeda otomatis setelah N menit (hidup di holder agar tidak hilang saat pindah video) ----
    private val handler = Handler(Looper.getMainLooper())
    private val _sleepEndsAt = MutableStateFlow<Long?>(null)
    val sleepEndsAt: StateFlow<Long?> = _sleepEndsAt.asStateFlow()
    private val sleepRunnable = Runnable { player.pause(); _sleepEndsAt.value = null }
    fun setSleepTimer(minutes: Int?) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes == null) { _sleepEndsAt.value = null; return }
        val ms = minutes * 60_000L
        _sleepEndsAt.value = System.currentTimeMillis() + ms
        handler.postDelayed(sleepRunnable, ms)
    }

    /**
     * Sumber data HTTP dengan header per-klien YouTube. Dibagi ke pemutar lain (Shorts) agar URL stream tidak
     * ditolak server karena User-Agent bawaan ExoPlayer tak cocok dengan klien pembuat URL.
     */
    /** Satu klien HTTP untuk semua media (utama, Shorts, unduhan): kolam koneksi bersama, HTTP/2, tanpa cache respons. */
    private val mediaClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequests = 32; maxRequestsPerHost = 16 })
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val mediaCache: SimpleCache? by lazy {
        runCatching {
            SimpleCache(File(context.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024), StandaloneDatabaseProvider(context))
        }.getOrNull()
    }

    /**
     * Kunci cache: URL googlevideo berganti tiap kali (tanda tangan/kedaluwarsa) padahal isinya sama, jadi kuncinya
     * id-stream + itag + ukuran. Siaran langsung, segmen (sq=), manifes & URL lain diberi kunci unik (tidak pernah basi).
     */
    private fun cacheKey(uri: android.net.Uri): String {
        val id = uri.getQueryParameter("id")
        val itag = uri.getQueryParameter("itag")
        val stable = uri.host?.endsWith("googlevideo.com") == true && uri.path == "/videoplayback" && id != null && itag != null &&
            uri.getQueryParameter("sq") == null && uri.getQueryParameter("live") == null
        return if (stable) "$id-$itag-${uri.getQueryParameter("clen").orEmpty()}" else uri.toString() + "#" + System.nanoTime()
    }

    /** true bila jaringan aktif berbayar (data seluler): prefetch dikurangi agar kuota hemat. */
    fun isMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return cm?.isActiveNetworkMetered == true
    }

    val dataSourceFactory: androidx.media3.datasource.DataSource.Factory by lazy {
        // Catatan: User-Agent TIDAK diatur di pabrik ini (di Media3 nilai itu menimpa header per-permintaan);
        // diatur per permintaan lewat ResolvingDataSource, mengikuti klien YouTube pembuat URL (param c=).
        val okHttp = OkHttpDataSource.Factory(mediaClient)
        // Cache disk 256 MB (LRU) khusus HTTP(S): menonton ulang / mundur / kembali ke video yang sama tidak mengunduh lagi.
        // File lokal (unduhan) tetap lewat DefaultDataSource tanpa cache.
        val cache = mediaCache
        val http: androidx.media3.datasource.DataSource.Factory =
            if (cache == null) okHttp
            else CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(okHttp)
                .setCacheKeyFactory { spec: DataSpec -> cacheKey(spec.uri) }
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ResolvingDataSource.Factory(DefaultDataSource.Factory(context, http)) { spec -> ClientHeaders.apply(spec) }
    }

    private fun create(): ExoPlayer {
        val dataSource = dataSourceFactory
        // Mulai putar secepat mungkin (0,5 dtk data cukup), setelah buffering ulang tunggu 2,5 dtk agar tidak
        // "buffer-putar-buffer" berulang. Buffer ke depan 30-90 dtk untuk jaringan seluler yang naik-turun, dan
        // 30 dtk buffer belakang supaya mundur/ulang beberapa detik langsung jalan tanpa mengunduh lagi.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 90_000, 500, 2_500)
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        // Dekoder cadangan: bila dekoder utama gagal inisialisasi (sering di HP murah), coba dekoder lain, bukan galat.
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Jaga CPU & Wi-Fi tetap hidup saat layar mati; tanpa ini stream putus di latar belakang.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also {
                it.trackSelectionParameters = defaultSelection(it, videoEnabled = true)
                it.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        handler.removeCallbacks(saveRunnable)
                        persistProgress()
                        if (isPlaying) handler.postDelayed(saveRunnable, 10_000)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) persistProgress() }
                })
            }
    }

    /** Batas resolusi untuk mode "Otomatis" pada sumber adaptif (DASH/HLS): 1080p bila "Kualitas tinggi" aktif, selain itu 720p. */
    fun autoMaxSize(): Pair<Int, Int> = if (library.highQuality.value) 1920 to 1080 else 1280 to 720

    /**
     * Tinggi video (px) yang dipilih mode "Otomatis" untuk sumber video-saja + audio-saja (tanpa ABR).
     * Dipilih dari kecepatan unduh yang dilaporkan jaringan aktif (agar mulai cepat & tidak tersendat):
     * >= 12 Mbps = 720p (1080p bila "Kualitas tinggi" aktif), >= 5 Mbps = 480p, >= 2 Mbps = 360p, lebih lambat = 240p.
     * Data seluler (berbayar) dibatasi maksimal 480p kecuali "Kualitas tinggi" aktif.
     */
    fun autoHeight(): Int {
        val high = library.highQuality.value
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val kbps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork)?.linkDownstreamBandwidthKbps }.getOrNull() ?: 0
        val byBandwidth = when {
            kbps <= 0 -> 720            // tak diketahui: perlakukan seperti sebelumnya
            kbps >= 12_000 -> if (high) 1080 else 720
            kbps >= 5_000 -> 480
            kbps >= 2_000 -> 360
            else -> 240
        }
        if (high) return maxOf(byBandwidth, if (kbps in 1..4_999) byBandwidth else 1080)
        return if (cm?.isActiveNetworkMetered == true) minOf(byBandwidth, 480) else byBandwidth
    }

    private fun defaultSelection(p: Player, videoEnabled: Boolean): TrackSelectionParameters =
        p.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setMaxVideoSize(autoMaxSize().first, autoMaxSize().second).setMaxAudioBitrate(128_000)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !videoEnabled)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .setPreferredTextLanguage(null)
            .build()

    /**
     * Putar [item]. [keepSettings] = true untuk video yang SAMA (ganti sumber/ulangi setelah galat):
     * pilihan kualitas & kecepatan pengguna dipertahankan. Video baru selalu mulai dari bawaan.
     * [videoEnabled] false bila aplikasi sedang di latar belakang (hanya audio, hemat data & baterai).
     */
    fun play(item: MediaItem, startPositionMs: Long? = null, keepSettings: Boolean = false, videoEnabled: Boolean = true, audioUrl: String? = null) {
        val p = player
        if (!keepSettings) {
            p.trackSelectionParameters = defaultSelection(p, videoEnabled)
            p.setPlaybackSpeed(1f)
            p.repeatMode = Player.REPEAT_MODE_OFF
        }
        val start = startPositionMs ?: positions.remove(item.mediaId) ?: library.progressOf(item.mediaId)
        if (audioUrl.isNullOrBlank()) p.setMediaItem(item, start)
        else {
            // Video-saja + audio-saja digabung (resolusi 480p-1080p, jauh di atas stream gabungan YouTube yang mentok 360p).
            // DefaultMediaSourceFactory tetap memasang takarir dari [item]; MergingMediaSource memakai MediaItem sumber pertama,
            // jadi mediaId & metadata notifikasi tetap milik video ini.
            val video = DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(item)
            val audio = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(audioUrl))
            p.setMediaSource(MergingMediaSource(video, audio), start)
        }
        p.prepare()
        p.playWhenReady = true
        startService()
    }

    /** Hidupkan/matikan trek video tanpa mengganggu pilihan kualitas pengguna. */
    fun setVideoEnabled(enabled: Boolean) {
        val p = player
        if (p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO) == !enabled) return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled).build()
    }

    /** Hentikan HANYA bila yang sedang dimuat memang [mediaId] ini (layar lain mungkin sudah mengambil alih pemutar). */
    fun stopIfCurrent(mediaId: String) {
        val p = player
        if (p.currentMediaItem?.mediaId != mediaId) return
        upNextId = null
        upPrevId = null
        persistProgress()
        if (p.playbackState != Player.STATE_ENDED && p.currentPosition > 3_000) positions[mediaId] = p.currentPosition
        p.stop()
        p.clearMediaItems()   // PlaybackService berhenti sendiri saat daftar kosong.
    }

    fun isCurrent(mediaId: String, uri: String): Boolean {
        val item = player.currentMediaItem ?: return false
        return item.mediaId == mediaId && item.localConfiguration?.uri?.toString() == uri &&
            player.playbackState != Player.STATE_IDLE && player.playerError == null
    }

    private fun startService() {
        // startService (bukan startForegroundService): dipanggil dari layar yang sedang tampil, dan Media3
        // sendiri menaikkan service ke foreground begitu pemutaran benar-benar berjalan.
        runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
    }
}
