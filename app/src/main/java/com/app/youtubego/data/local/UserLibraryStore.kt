package com.app.youtubego.data.local

import android.content.Context
import com.app.youtubego.domain.model.Playlist
import com.app.youtubego.domain.model.VideoSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class StoredVideo(
    val id: String, val title: String, val author: String, val authorId: String,
    val avatar: String, val thumb: String, val length: Long, val views: Long, val published: Long, val live: Boolean,
)

@Serializable
private data class StoredPlaylist(val id: String, val name: String, val ids: List<String> = emptyList())

private fun VideoSummary.toStored() = StoredVideo(id, title, author, authorId, authorAvatarUrl, thumbnailUrl, lengthSeconds, viewCount, published, isLive)
private fun StoredVideo.toDomain() = VideoSummary(id, title, author, authorId, avatar, thumb, length, views, published, live)

/**
 * Koleksi pribadi lokal (akun lokal, tersimpan di perangkat): video disukai, tonton nanti, riwayat,
 * playlist, channel yang dilanggani, serta preferensi. Daftar id disimpan URUT (terbaru di depan);
 * metadata video disimpan terpisah agar halaman koleksi bisa menampilkan kartu tanpa memanggil jaringan lagi.
 */
@Singleton
class UserLibraryStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("user_library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _liked = MutableStateFlow<Set<String>>(readOrdered("liked"))
    private val _saved = MutableStateFlow<Set<String>>(readOrdered("saved"))
    private val _subscribed = MutableStateFlow<Set<String>>(readOrdered("subscribed"))
    private val _history = MutableStateFlow(readOrdered("history").toList())
    private val _meta = MutableStateFlow(readMeta())
    private val _playlists = MutableStateFlow(readPlaylists())
    private val _autoplay = MutableStateFlow(prefs.getBoolean("autoplay", true))
    private val _highQuality = MutableStateFlow(prefs.getBoolean("high_quality", false))
    private val _backgroundPlay = MutableStateFlow(prefs.getBoolean("background_play", true))
    private val _wifiOnlyDownloads = MutableStateFlow(prefs.getBoolean("wifi_only_downloads", true))
    private val _historyPaused = MutableStateFlow(prefs.getBoolean("history_paused", false))

    val liked: StateFlow<Set<String>> = _liked.asStateFlow()
    val saved: StateFlow<Set<String>> = _saved.asStateFlow()
    val subscribed: StateFlow<Set<String>> = _subscribed.asStateFlow()
    /** Id video yang pernah ditonton, terbaru di depan. */
    val history: StateFlow<List<String>> = _history.asStateFlow()
    /** Metadata video yang dirujuk koleksi di atas (id -> ringkasan). */
    val meta: StateFlow<Map<String, VideoSummary>> = _meta.asStateFlow()
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()
    /** false = mulai dari kualitas terendah (hemat data, bawaan); true = mulai dari kualitas tertinggi yang tersedia. */
    val highQuality: StateFlow<Boolean> = _highQuality.asStateFlow()
    val backgroundPlay: StateFlow<Boolean> = _backgroundPlay.asStateFlow()
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()
    val historyPaused: StateFlow<Boolean> = _historyPaused.asStateFlow()

    // ---- Suka / Tonton nanti / Langganan ----
    fun toggleLiked(videoId: String) = toggle("liked", _liked, videoId)
    fun toggleSaved(videoId: String) = toggle("saved", _saved, videoId)
    fun toggleSubscribed(channelId: String) = toggle("subscribed", _subscribed, channelId)
    fun toggleLiked(video: VideoSummary) { remember(video); toggleLiked(video.id) }
    fun toggleSaved(video: VideoSummary) { remember(video); toggleSaved(video.id) }

    // ---- Riwayat ----
    @Synchronized
    fun addHistory(video: VideoSummary) {
        if (_historyPaused.value || video.id.isBlank()) return
        remember(video)
        val next = (listOf(video.id) + _history.value.filter { it != video.id }).take(MAX_HISTORY)
        _history.value = next
        prefs.edit().putString("history", next.joinToString(",")).apply()
    }

    @Synchronized
    fun removeHistory(videoId: String) {
        val next = _history.value.filter { it != videoId }
        _history.value = next
        prefs.edit().putString("history", next.joinToString(",")).apply()
    }

    @Synchronized
    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().putString("history", "").apply()
    }

    // ---- Playlist ----
    @Synchronized
    fun createPlaylist(name: String, firstVideo: VideoSummary? = null): String {
        val id = UUID.randomUUID().toString().take(8)
        firstVideo?.let { remember(it) }
        val list = Playlist(id, name.trim().ifEmpty { "Playlist baru" }.take(60), listOfNotNull(firstVideo?.id))
        savePlaylists(listOf(list) + _playlists.value)
        return id
    }

    @Synchronized
    fun renamePlaylist(id: String, name: String) {
        val n = name.trim().take(60)
        if (n.isEmpty()) return
        savePlaylists(_playlists.value.map { if (it.id == id) it.copy(name = n) else it })
    }

    @Synchronized
    fun deletePlaylist(id: String) = savePlaylists(_playlists.value.filter { it.id != id })

    /** Tambah bila belum ada, hapus bila sudah ada. */
    @Synchronized
    fun togglePlaylistVideo(playlistId: String, video: VideoSummary) {
        remember(video)
        savePlaylists(_playlists.value.map { p ->
            if (p.id != playlistId) p
            else if (video.id in p.videoIds) p.copy(videoIds = p.videoIds - video.id)
            else p.copy(videoIds = listOf(video.id) + p.videoIds)
        })
    }

    @Synchronized
    fun removeFromPlaylist(playlistId: String, videoId: String) =
        savePlaylists(_playlists.value.map { if (it.id == playlistId) it.copy(videoIds = it.videoIds - videoId) else it })

    // ---- Preferensi ----
    fun setAutoplay(on: Boolean) { _autoplay.value = on; prefs.edit().putBoolean("autoplay", on).apply() }
    fun setBackgroundPlay(on: Boolean) { _backgroundPlay.value = on; prefs.edit().putBoolean("background_play", on).apply() }
    fun setHighQuality(on: Boolean) { _highQuality.value = on; prefs.edit().putBoolean("high_quality", on).apply() }
    fun setWifiOnlyDownloads(on: Boolean) { _wifiOnlyDownloads.value = on; prefs.edit().putBoolean("wifi_only_downloads", on).apply() }
    fun setHistoryPaused(on: Boolean) { _historyPaused.value = on; prefs.edit().putBoolean("history_paused", on).apply() }

    /** Simpan metadata sebuah video agar bisa ditampilkan di halaman koleksi (dibatasi agar prefs tidak membengkak). */
    @Synchronized
    fun remember(video: VideoSummary) {
        if (video.id.isBlank() || video.title.isBlank()) return
        val m = LinkedHashMap(_meta.value)
        m.remove(video.id)
        m[video.id] = video
        // Pangkas hanya id yang tidak dirujuk koleksi mana pun, mulai dari yang tertua.
        if (m.size > MAX_META) {
            val referenced = HashSet<String>().apply {
                addAll(_liked.value); addAll(_saved.value); addAll(_history.value)
                _playlists.value.forEach { addAll(it.videoIds) }
            }
            val iter = m.keys.iterator()
            while (m.size > MAX_META && iter.hasNext()) { if (iter.next() !in referenced) iter.remove() }
        }
        _meta.value = m
        prefs.edit().putString("meta", json.encodeToString(ListSerializer(StoredVideo.serializer()), m.values.map { it.toStored() })).apply()
    }

    private fun savePlaylists(next: List<Playlist>) {
        _playlists.value = next
        prefs.edit().putString("playlists", json.encodeToString(ListSerializer(StoredPlaylist.serializer()), next.map { StoredPlaylist(it.id, it.name, it.videoIds) })).apply()
    }

    private fun readMeta(): Map<String, VideoSummary> {
        val raw = prefs.getString("meta", null) ?: return emptyMap()
        val list = runCatching { json.decodeFromString(ListSerializer(StoredVideo.serializer()), raw) }.getOrDefault(emptyList())
        return LinkedHashMap<String, VideoSummary>().apply { list.forEach { put(it.id, it.toDomain()) } }
    }

    private fun readPlaylists(): List<Playlist> {
        val raw = prefs.getString("playlists", null) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(StoredPlaylist.serializer()), raw) }.getOrDefault(emptyList())
            .map { Playlist(it.id, it.name, it.ids) }
    }

    /** Daftar berurut. Versi lama menyimpan StringSet tanpa urutan: dipakai sebagai cadangan sekali baca. */
    private fun readOrdered(key: String): LinkedHashSet<String> {
        val s = runCatching { prefs.getString(key, null) }.getOrNull()
        if (s != null) return LinkedHashSet(s.split(",").filter { it.isNotBlank() })
        return runCatching { LinkedHashSet(prefs.getStringSet(key, emptySet()) ?: emptySet()) }.getOrDefault(LinkedHashSet())
    }

    @Synchronized
    private fun toggle(key: String, flow: MutableStateFlow<Set<String>>, id: String) {
        if (id.isBlank()) return
        val next: Set<String> = if (id in flow.value) flow.value - id else LinkedHashSet<String>().apply { add(id); addAll(flow.value) }
        flow.value = next
        // Hapus kunci StringSet versi lama sebelum menulis string berurut dengan nama yang sama.
        prefs.edit().remove(key).putString(key, next.joinToString(",")).apply()
    }

    private companion object {
        const val MAX_HISTORY = 200
        const val MAX_META = 400
    }
}
