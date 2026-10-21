package com.app.youtubego.playback

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Menautkan pemutar ke siklus hidup ACTIVITY (bukan layar navigasi: entri back-stack bisa "berhenti"
 * hanya karena pindah layar, padahal aplikasi masih tampil).
 * - ON_STOP (layar mati / ke Beranda): latar belakang aktif -> matikan trek video, suara jalan terus;
 *   nonaktif -> jeda.
 * - ON_START: nyalakan lagi trek video.
 * Mode PiP tidak memicu ON_STOP, jadi video tetap tampil di jendela mengambang.
 */
@Composable
fun BackgroundPlaybackEffect(holder: PlayerHolder, backgroundPlay: Boolean) {
    val context = LocalContext.current
    DisposableEffect(holder, backgroundPlay) {
        val lifecycle = (context.findComponentActivity())?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (backgroundPlay) holder.setVideoEnabled(false) else holder.player.pause()
                Lifecycle.Event.ON_START -> holder.setVideoEnabled(true)
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
}

fun android.content.Context.findComponentActivity(): ComponentActivity? {
    var c = this
    while (c is ContextWrapper) { if (c is ComponentActivity) return c; c = c.baseContext }
    return null
}

