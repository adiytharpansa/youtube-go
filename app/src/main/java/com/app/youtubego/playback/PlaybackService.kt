package com.app.youtubego.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.app.youtubego.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Service foreground (tipe mediaPlayback) yang membuat pemutaran tetap hidup di latar belakang.
 * Media3 otomatis menampilkan notifikasi media (putar/jeda, seekbar, layar kunci) dan menaikkan
 * service ke foreground selama memutar. Berhenti sendiri saat pemutar dikosongkan.
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    @Inject lateinit var holder: PlayerHolder
    private var session: MediaSession? = null

    private val idleWatcher = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) && player.mediaItemCount == 0) stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = holder.player
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, SkipPlayer(player)).setSessionActivity(open).build()
        player.addListener(idleWatcher)
    }

    /**
     * Pemutar hanya memuat satu video, jadi Media3 tidak menampilkan tombol Sebelumnya/Berikutnya di notifikasi.
     * Pembungkus ini menyatakan keduanya tersedia: Berikutnya = video rekomendasi pertama (lewat holder), Sebelumnya = ulang dari awal.
     */
    private inner class SkipPlayer(p: Player) : ForwardingPlayer(p) {
        override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
            .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
        override fun hasNextMediaItem(): Boolean = holder.upNextId != null
        override fun seekToNext() { holder.requestNext() }
        override fun seekToNextMediaItem() { holder.requestNext() }
        override fun seekToPrevious() { seekTo(0) }
        override fun seekToPreviousMediaItem() { seekTo(0) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Aplikasi digeser dari recent: lanjut bila sedang memutar (seperti pemutar musik), selain itu matikan. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = holder.player
        if (!p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            p.stop(); p.clearMediaItems(); stopSelf()
        }
    }

    override fun onDestroy() {
        holder.player.removeListener(idleWatcher)
        session?.release()
        session = null
        super.onDestroy()
    }
}
