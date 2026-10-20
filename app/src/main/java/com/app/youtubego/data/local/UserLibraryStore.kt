package com.app.youtubego.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.app.youtubego.domain.model.VideoSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Koleksi lokal persisten: suka, tonton nanti, langganan, riwayat, dan cache metadata video. */
@Singleton
class UserLibraryStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("user_library", Context.MODE_PRIVATE)
    private val _liked = MutableStateFlow(readIds("liked"))
    private val _saved = MutableStateFlow(readIds("saved"))
    private val _subscribed = MutableStateFlow(readIds("subscribed"))
    private val _autoplay = MutableStateFlow(prefs.getBoolean("autoplay", true))
    private val _history = MutableStateFlow(readSummaries("history"))
    private val _summaries = MutableStateFlow(readSummaryMap("summaries"))

    val liked: StateFlow<Set<String>> = _liked.asStateFlow()
    val saved: StateFlow<Set<String>> = _saved.asStateFlow()
    val subscribed: StateFlow<Set<String>> = _subscribed.asStateFlow()
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()
    val history: StateFlow<List<VideoSummary>> = _history.asStateFlow()
    val summaries: StateFlow<Map<String, VideoSummary>> = _summaries.asStateFlow()

    fun toggleLiked(videoId: String) = toggle("liked", _liked, videoId)
    fun toggleSaved(videoId: String) = toggle("saved", _saved, videoId)
    fun toggleSubscribed(channelId: String) = toggle("subscribed", _subscribed, channelId)
    fun setAutoplay(on: Boolean) {
        _autoplay.value = on
        prefs.edit().putBoolean("autoplay", on).apply()
    }

    /** Simpan metadata ringan agar riwayat/koleksi tetap bisa ditampilkan offline. */
    @Synchronized
    fun recordWatch(video: VideoSummary) {
        cacheSummary(video)
        val next = (listOf(video) + _history.value.filterNot { it.id == video.id }).take(MAX_HISTORY)
        _history.value = next
        prefs.edit().putString("history", JSONArray().apply { next.forEach { put(it.toJson()) } }.toString()).apply()
    }

    @Synchronized
    fun cacheSummary(video: VideoSummary) {
        val next = LinkedHashMap(_summaries.value)
        next[video.id] = video
        while (next.size > MAX_CACHED_SUMMARIES) next.remove(next.keys.first())
        _summaries.value = next
        prefs.edit().putString(
            "summaries",
            JSONObject().apply { next.forEach { (id, summary) -> put(id, summary.toJson()) } }.toString()
        ).apply()
    }

    @Synchronized
    fun removeFromHistory(videoId: String) {
        val next = _history.value.filterNot { it.id == videoId }
        _history.value = next
        prefs.edit().putString("history", JSONArray().apply { next.forEach { put(it.toJson()) } }.toString()).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("history").apply()
    }

    private fun readIds(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun readSummaries(key: String): List<VideoSummary> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.toSummaryOrNull()
        }
    }.getOrDefault(emptyList())

    private fun readSummaryMap(key: String): Map<String, VideoSummary> = runCatching {
        val obj = JSONObject(prefs.getString(key, "{}"))
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                obj.optJSONObject(id)?.toSummaryOrNull()?.let { put(id, it) }
            }
        }
    }.getOrDefault(emptyMap())

    @Synchronized
    private fun toggle(key: String, flow: MutableStateFlow<Set<String>>, id: String) {
        if (id.isBlank()) return
        val next = if (id in flow.value) flow.value - id else flow.value + id
        flow.value = next
        prefs.edit().putStringSet(key, HashSet(next)).apply()
    }

    private fun VideoSummary.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("author", author); put("authorId", authorId)
        put("authorAvatarUrl", authorAvatarUrl); put("thumbnailUrl", thumbnailUrl)
        put("lengthSeconds", lengthSeconds); put("viewCount", viewCount); put("published", published); put("isLive", isLive)
    }

    private fun JSONObject.toSummaryOrNull(): VideoSummary? = runCatching {
        val id = getString("id")
        VideoSummary(
            id = id, title = optString("title"), author = optString("author"), authorId = optString("authorId"),
            authorAvatarUrl = optString("authorAvatarUrl"), thumbnailUrl = optString("thumbnailUrl"),
            lengthSeconds = optLong("lengthSeconds"), viewCount = optLong("viewCount"),
            published = optLong("published"), isLive = optBoolean("isLive")
        )
    }.getOrNull()

    private companion object {
        const val MAX_HISTORY = 100
        const val MAX_CACHED_SUMMARIES = 300
    }
}
