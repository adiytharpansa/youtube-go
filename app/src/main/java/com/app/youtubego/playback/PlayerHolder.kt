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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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
    val dataSourceFactory: androidx.media3.datasource.DataSource.Factory by lazy {
        // Catatan: User-Agent TIDAK diatur di pabrik ini (di Media3 nilai itu menimpa header per-permintaan);
        // diatur per permintaan lewat ResolvingDataSource, mengikuti klien YouTube pembuat URL (param c=).
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        ResolvingDataSource.Factory(DefaultDataSource.Factory(context, http)) { spec -> ClientHeaders.apply(spec) }
    }

    private fun create(): ExoPlayer {
        val dataSource = dataSourceFactory
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

    /** Batas resolusi untuk mode "Otomatis" pada sumber adaptif (DASH/HLS): 1080p bila "Kualitas tinggi" aktif, selain itu 720p. */
    fun autoMaxSize(): Pair<Int, Int> = if (library.highQuality.value) 1920 to 1080 else 1280 to 720

    /**
     * Tinggi video (px) yang dipilih mode "Otomatis" untuk sumber video-saja + audio-saja (tanpa ABR):
     * "Kualitas tinggi" = 1080p; selain itu 720p di Wi-Fi dan 480p di data seluler (jaringan berbayar).
     */
    fun autoHeight(): Int {
        if (library.highQuality.value) return 1080
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return if (cm?.isActiveNetworkMetered == true) 480 else 720
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
