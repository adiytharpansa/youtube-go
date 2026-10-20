package com.app.youtubego.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class LocalPlaylist(val id: String, val name: String, val videoIds: List<String>)

@Singleton
class PlaylistStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("local_playlists", Context.MODE_PRIVATE)
    private val _playlists = MutableStateFlow(read())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    @Synchronized
    fun create(name: String): String? {
        val clean = name.trim().take(60)
        if (clean.isBlank()) return null
        val playlist = LocalPlaylist(UUID.randomUUID().toString(), clean, emptyList())
        update(listOf(playlist) + _playlists.value)
        return playlist.id
    }

    @Synchronized
    fun delete(id: String) = update(_playlists.value.filterNot { it.id == id })

    @Synchronized
    fun addVideo(playlistId: String, videoId: String) {
        if (videoId.isBlank()) return
        update(_playlists.value.map { p ->
            if (p.id == playlistId && videoId !in p.videoIds) p.copy(videoIds = listOf(videoId) + p.videoIds) else p
        })
    }

    @Synchronized
    fun removeVideo(playlistId: String, videoId: String) {
        update(_playlists.value.map { p -> if (p.id == playlistId) p.copy(videoIds = p.videoIds - videoId) else p })
    }

    private fun update(next: List<LocalPlaylist>) {
        _playlists.value = next
        prefs.edit().putString("items", JSONArray().apply {
            next.forEach { p -> put(JSONObject().apply {
                put("id", p.id); put("name", p.name); put("videoIds", JSONArray(p.videoIds))
            }) }
        }.toString()).apply()
    }

    private fun read(): List<LocalPlaylist> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val ids = obj.optJSONArray("videoIds") ?: JSONArray()
            LocalPlaylist(
                id = obj.optString("id"), name = obj.optString("name"),
                videoIds = (0 until ids.length()).map { ids.optString(it) }.filter { it.isNotBlank() }
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }.getOrDefault(emptyList())
}
