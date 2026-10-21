package com.app.youtubego.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
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
class PlayerHolder @Inject constructor(@ApplicationContext private val context: Context) {

    val player: ExoPlayer by lazy { create() }

    /** Posisi terakhir per mediaId, agar kembali ke video yang sama melanjutkan, bukan dari nol. */
    private val positions = HashMap<String, Long>()

    private fun create(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val dataSource = DefaultDataSource.Factory(context, http)
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
            .also { it.trackSelectionParameters = defaultSelection(it, videoEnabled = true) }
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
        p.setMediaItem(item, startPositionMs ?: positions.remove(item.mediaId) ?: 0L)
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
