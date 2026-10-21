package com.app.youtubego.data.local

import android.content.Context
import android.net.ConnectivityManager
import com.app.youtubego.data.newpipe.DeviceStreamSource
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.playback.ClientHeaders
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

enum class DownloadStatus { DOWNLOADING, PAUSED, DONE, FAILED }

data class DownloadItem(
    val video: VideoSummary,
    val status: DownloadStatus,
    val bytes: Long = 0,
    val total: Long = 0,
    val error: String = "",
) {
    val progress: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
}

@Serializable
private data class StoredDownload(
    val id: String, val title: String, val author: String, val authorId: String, val thumb: String,
    val length: Long, val views: Long, val published: Long, val status: String, val bytes: Long, val total: Long,
)

/**
 * Unduhan sederhana untuk tontonan offline: mengunduh stream gabungan (video+audio) kualitas terendah yang layak
 * ke penyimpanan privat aplikasi, bisa dilanjutkan (HTTP Range). Berjalan selama proses aplikasi hidup;
 * bila aplikasi dimatikan sistem, unduhan berstatus "Dijeda" dan bisa dilanjutkan dari titik terakhir.
 */
@Singleton
class DownloadStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceSource: DeviceStreamSource,
    private val repo: VideoRepository,
    private val library: UserLibraryStore,
) {
    private val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val dir: File = File(context.filesDir, "downloads").apply { mkdirs() }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    fun fileFor(videoId: String): File = File(dir, "$videoId.mp4")
    fun isDone(videoId: String): Boolean = _items.value.any { it.video.id == videoId && it.status == DownloadStatus.DONE } && fileFor(videoId).exists()
    fun totalSizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Mulai atau lanjutkan unduhan. Aman dipanggil berulang. */
    @Synchronized
    fun start(video: VideoSummary) {
        if (video.id.isBlank()) return
        val existing = _items.value.firstOrNull { it.video.id == video.id }
        if (existing?.status == DownloadStatus.DONE && fileFor(video.id).exists()) return
        if (jobs[video.id]?.isActive == true) return
        library.remember(video)
        upsert(DownloadItem(video, DownloadStatus.DOWNLOADING, existing?.bytes ?: 0, existing?.total ?: 0))
        // Ulangi setelah gagal/dijeda: URL lama bisa kedaluwarsa atau ditolak, jadi lewati cache ekstraksi.
        val fresh = existing != null && existing.status != DownloadStatus.DONE
        jobs[video.id] = scope.launch { download(video, fresh) }
    }

    @Synchronized
    fun pause(videoId: String) {
        jobs.remove(videoId)?.cancel()
        update(videoId) { if (it.status == DownloadStatus.DOWNLOADING) it.copy(status = DownloadStatus.PAUSED) else it }
    }

    @Synchronized
    fun remove(videoId: String) {
        jobs.remove(videoId)?.cancel()
        fileFor(videoId).delete()
        _items.update { l -> l.filter { it.video.id != videoId } }
        persist()
    }

    @Synchronized
    fun removeAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        dir.listFiles()?.forEach { it.delete() }
        _items.value = emptyList()
        persist()
    }

    private suspend fun download(video: VideoSummary, fresh: Boolean) {
        try {
            if (library.wifiOnlyDownloads.value && isMetered()) {
                fail(video.id, "Menunggu Wi-Fi. Matikan \"Unduh hanya lewat Wi-Fi\" di Pengaturan untuk memakai data seluler.")
                return
            }
            val url = resolveUrl(video.id, fresh) ?: run { fail(video.id, "Tautan unduhan tidak ditemukan. Coba lagi nanti."); return }
            val file = fileFor(video.id)
            var offset = if (file.exists()) file.length() else 0L
            // Header harus cocok dengan klien YouTube pembuat URL (param c=), kalau tidak server menjawab 403.
            val req = Request.Builder().url(url)
                .apply { ClientHeaders.forUrl(url).forEach { (k, v) -> header(k, v) } }
                .apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
            client.newCall(req).execute().use { resp ->
                // 416 saat melanjutkan = berkas sudah lengkap (proses mati tepat sebelum status DONE tersimpan).
                if (resp.code == 416 && offset > 0) {
                    update(video.id) { it.copy(status = DownloadStatus.DONE, bytes = offset, total = offset, error = "") }
                    persist()
                    return
                }
                if (!resp.isSuccessful) { fail(video.id, "Server menolak unduhan (galat ${resp.code})."); return }
                val body = resp.body ?: run { fail(video.id, "Respons kosong dari server."); return }
                val resumed = resp.code == 206
                if (!resumed) { offset = 0L; file.delete() }
                val total = if (body.contentLength() > 0) body.contentLength() + offset else 0L
                var written = offset
                var lastUi = 0L
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(offset)
                    val buf = ByteArray(64 * 1024)
                    body.byteStream().use { input ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            written += n
                            val now = System.currentTimeMillis()
                            if (now - lastUi > 400) { lastUi = now; update(video.id) { it.copy(bytes = written, total = total) } }
                        }
                    }
                }
                update(video.id) { it.copy(status = DownloadStatus.DONE, bytes = written, total = if (total > 0) total else written, error = "") }
                persist()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(video.id, "Unduhan terputus. Ketuk Lanjutkan untuk mencoba lagi.")
        }
    }

    /** Jalur perangkat dulu (IP pengguna), lalu API server; ambil stream gabungan terendah yang layak (>= 240p bila ada). */
    private suspend fun resolveUrl(id: String, fresh: Boolean): String? {
        val dev = withTimeoutOrNull(12_000) { deviceSource.resolve(id, force = fresh).getOrNull()?.combined }.orEmpty()
        pick(dev.map { it.url to it.qualityLabel })?.let { return it }
        val api = repo.videoDetail(id).getOrNull()?.combinedStreams.orEmpty()
        return pick(api.map { it.url to it.qualityLabel })
    }

    private fun pick(options: List<Pair<String, String>>): String? {
        val valid = options.filter { it.first.isNotBlank() }
        return (valid.firstOrNull { (it.second.removeSuffix("p").toIntOrNull() ?: 0) >= 240 } ?: valid.firstOrNull())?.first
    }

    private fun isMetered(): Boolean =
        runCatching { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered }.getOrDefault(false)

    private fun fail(id: String, message: String) {
        update(id) { it.copy(status = DownloadStatus.FAILED, error = message) }
        persist()
    }

    private fun upsert(item: DownloadItem) {
        _items.update { l -> if (l.any { it.video.id == item.video.id }) l.map { if (it.video.id == item.video.id) item else it } else listOf(item) + l }
        persist()
    }

    private fun update(id: String, f: (DownloadItem) -> DownloadItem) {
        _items.update { l -> l.map { if (it.video.id == id) f(it) else it } }
    }

    private fun persist() {
        val list = _items.value.map {
            val v = it.video
            StoredDownload(v.id, v.title, v.author, v.authorId, v.thumbnailUrl, v.lengthSeconds, v.viewCount, v.published, it.status.name, it.bytes, it.total)
        }
        prefs.edit().putString("items", json.encodeToString(ListSerializer(StoredDownload.serializer()), list)).apply()
    }

    private fun load(): List<DownloadItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(StoredDownload.serializer()), raw) }.getOrDefault(emptyList()).map {
            val file = fileFor(it.id)
            val saved = runCatching { DownloadStatus.valueOf(it.status) }.getOrDefault(DownloadStatus.FAILED)
            // Proses sebelumnya mati saat mengunduh -> Dijeda; berkas hilang -> buang.
            val status = when {
                saved == DownloadStatus.DONE && !file.exists() -> DownloadStatus.FAILED
                saved == DownloadStatus.DOWNLOADING -> DownloadStatus.PAUSED
                else -> saved
            }
            DownloadItem(
                VideoSummary(it.id, it.title, it.author, it.authorId, "", it.thumb, it.length, it.views, it.published, false),
                status, if (file.exists()) file.length() else 0L, it.total,
                if (status == DownloadStatus.FAILED) "Berkas unduhan tidak ditemukan." else "",
            )
        }
    }
}
