package com.app.youtubego.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Koleksi pribadi lokal: video disukai, tonton nanti, channel yang dilanggani, dan preferensi putar otomatis. */
@Singleton
class UserLibraryStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("user_library", Context.MODE_PRIVATE)
    private val _liked = MutableStateFlow(read("liked"))
    private val _saved = MutableStateFlow(read("saved"))
    private val _subscribed = MutableStateFlow(read("subscribed"))
    private val _autoplay = MutableStateFlow(prefs.getBoolean("autoplay", true))
    val liked: StateFlow<Set<String>> = _liked.asStateFlow()
    val saved: StateFlow<Set<String>> = _saved.asStateFlow()
    val subscribed: StateFlow<Set<String>> = _subscribed.asStateFlow()
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    fun toggleLiked(videoId: String) = toggle("liked", _liked, videoId)
    fun toggleSaved(videoId: String) = toggle("saved", _saved, videoId)
    fun toggleSubscribed(channelId: String) = toggle("subscribed", _subscribed, channelId)
    fun setAutoplay(on: Boolean) { _autoplay.value = on; prefs.edit().putBoolean("autoplay", on).apply() }

    private fun read(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    @Synchronized
    private fun toggle(key: String, flow: MutableStateFlow<Set<String>>, id: String) {
        if (id.isBlank()) return
        val next = if (id in flow.value) flow.value - id else flow.value + id
        flow.value = next
        prefs.edit().putStringSet(key, HashSet(next)).apply()
    }
}
