package com.app.youtubego.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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

    private fun create(): ExoPlayer {
        // Catatan: User-Agent TIDAK diatur di pabrik ini (di Media3 nilai itu menimpa header per-permintaan);
        // diatur per permintaan lewat ResolvingDataSource di bawah, mengikuti klien YouTube pembuat URL (param c=).
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(context, http)) { spec -> withClientHeaders(spec) }
        // Buffer lebih lapang dari bawaan untuk jaringan seluler yang naik-turun; mulai putar cepat (1,5 dtk),
        // setelah buffering ulang tunggu 4 dtk agar tidak "buffer-putar-buffer" berulang.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(25_000, 60_000, 1_500, 4_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        return ExoPlayer.Builder(context)
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

    /**
     * URL googlevideo terikat ke klien YouTube yang membuatnya (c=ANDROID / IOS / WEB ...). Server menolak (403/5xx)
     * bila User-Agent tidak cocok dengan klien itu, jadi setiap permintaan memakai UA yang sesuai.
     */
    private fun withClientHeaders(spec: DataSpec): DataSpec {
        val uri = spec.uri
        val host = uri.host.orEmpty()
        val client = uri.getQueryParameter("c")
        val ua = when (client) {
            "ANDROID" -> ytUserAgent("getAndroidUserAgent") ?: UA
            "IOS" -> ytUserAgent("getIosUserAgent") ?: UA
            else -> UA
        }
        val headers = HashMap(spec.httpRequestHeaders)
        headers["User-Agent"] = ua
        if (host.endsWith("googlevideo.com") && (client == null || client.startsWith("WEB") || client.startsWith("MWEB"))) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com/"
        }
        return spec.buildUpon().setHttpRequestHeaders(headers).build()
    }

    private val uaCache = HashMap<String, String?>()
    /** UA resmi klien dari NewPipeExtractor (lewat refleksi supaya build tak patah bila nama berubah antar-versi). */
    private fun ytUserAgent(method: String): String? = synchronized(uaCache) {
        uaCache.getOrPut(method) {
            runCatching {
                val c = Class.forName("org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper")
                val loc = Class.forName("org.schabi.newpipe.extractor.localization.Localization")
                c.getMethod(method, loc).invoke(null, null) as? String
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    private fun defaultSelection(p: Player, videoEnabled: Boolean): TrackSelectionParameters =
        p.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setMaxVideoSize(1280, 720).setMaxAudioBitrate(128_000)
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !videoEnabled)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .setPreferredTextLanguage(null)
            .build()

    /**
     * Putar [item]. [keepSettings] = true untuk video yang SAMA (ganti sumber/ulangi setelah galat):
     * pilihan kualitas & kecepatan pengguna dipertahankan. Video baru selalu mulai dari bawaan.
     * [videoEnabled] false bila aplikasi sedang di latar belakang (hanya audio, hemat data & baterai).
     */
    fun play(item: MediaItem, startPositionMs: Long? = null, keepSettings: Boolean = false, videoEnabled: Boolean = true) {
        val p = player
        if (!keepSettings) {
            p.trackSelectionParameters = defaultSelection(p, videoEnabled)
            p.setPlaybackSpeed(1f)
            p.repeatMode = Player.REPEAT_MODE_OFF
        }
        p.setMediaItem(item, startPositionMs ?: positions.remove(item.mediaId) ?: library.progressOf(item.mediaId))
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

    private companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
