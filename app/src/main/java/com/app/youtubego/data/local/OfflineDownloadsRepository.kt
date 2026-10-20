package com.app.youtubego.data.local

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import com.app.youtubego.data.newpipe.DeviceStreamSource
import com.app.youtubego.domain.model.VideoSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** DownloadManager wrapper: progressive streams go to the app's Movies/YouTubeGO directory. */
data class OfflineVideo(
    val downloadId: Long,
    val video: VideoSummary,
    val status: Int,
    val progress: Int,
    val downloadedUri: Uri? = null,
)

@Singleton
class OfflineDownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: DeviceStreamSource,
    private val library: UserLibraryStore,
    @Named("io") private val io: CoroutineDispatcher,
) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = context.getSharedPreferences("offline_downloads", Context.MODE_PRIVATE)
    private val _records = MutableStateFlow<List<OfflineVideo>>(emptyList())
    val records: StateFlow<List<OfflineVideo>> = _records.asStateFlow()
    private var ids: List<Pair<Long, String>> = readIds()

    suspend fun enqueue(video: VideoSummary): Result<Boolean> = withContext(io) {
        runCatching {
            if (ids.any { it.second == video.id }) return@runCatching false
            val playback = source.resolve(video.id).getOrThrow()
            val stream = playback.combined.firstOrNull { it.url.isNotBlank() }
                ?: throw IllegalStateException("Video ini tidak menyediakan unduhan progresif.")
            library.cacheSummary(video)
            val safeId = video.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val request = DownloadManager.Request(Uri.parse(stream.url))
                .setTitle(video.title.ifBlank { "Video YouTube GO" })
                .setDescription("YouTube GO · ${video.author}")
                .setMimeType(stream.mimeType.ifBlank { "video/mp4" })
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, "YouTubeGO_$safeId.mp4")
            val downloadId = manager.enqueue(request)
            ids = listOf(downloadId to video.id) + ids
            persistIds()
            refreshNow()
            true
        }
    }

    suspend fun refresh() = withContext(io) { refreshNow() }

    suspend fun remove(downloadId: Long) = withContext(io) {
        manager.remove(downloadId)
        ids = ids.filterNot { it.first == downloadId }
        persistIds()
        refreshNow()
    }

    private fun refreshNow() {
        val cache = library.summaries.value
        _records.value = ids.mapNotNull { (id, videoId) ->
            val video = cache[videoId] ?: return@mapNotNull null
            val query = DownloadManager.Query().setFilterById(id)
            manager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val percent = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                OfflineVideo(id, video, status, percent, if (status == DownloadManager.STATUS_SUCCESSFUL) manager.getUriForDownloadedFile(id) else null)
            }
        }
    }

    private fun readIds(): List<Pair<Long, String>> = runCatching {
        val array = JSONArray(prefs.getString("records", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optLong("downloadId", -1L)
            val videoId = item.optString("videoId")
            if (id > 0L && videoId.isNotBlank()) id to videoId else null
        }
    }.getOrDefault(emptyList())

    private fun persistIds() {
        prefs.edit().putString(
            "records",
            JSONArray().apply { ids.forEach { (id, videoId) -> put(org.json.JSONObject().put("downloadId", id).put("videoId", videoId)) } }.toString()
        ).apply()
    }
}
