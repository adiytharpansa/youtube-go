package com.app.youtubego.data.newpipe

import com.app.youtubego.domain.model.StreamOption
import com.app.youtubego.domain.model.VideoSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hasil ekstraksi di perangkat: cukup untuk memutar video TANPA server perantara
 * (permintaan berangkat dari IP pengguna), plus metadata cadangan bila API utama gagal.
 */
data class DevicePlayback(
    val summary: VideoSummary,
    val description: String,
    /** Stream gabungan progresif (video+audio), terurut kualitas menaik. */
    val combined: List<StreamOption>,
    val dashUrl: String?,
    val hlsUrl: String?,
    val likeCount: Long = 0L,
    val subscriberCount: Long = 0L,
)

/**
 * Jalur putar utama (spike NewPipeExtractor): menyelesaikan stream langsung di HP.
 * Berbeda total dari jalur server — YouTube menjawab IP rumahan/seluler pengguna,
 * bukan IP pusat data, sehingga dinding yang memblokir server tidak berlaku di sini.
 */
@Singleton
class DeviceStreamSource @Inject constructor(
    private val downloader: OkHttpNewPipeDownloader,
    @Named("io") private val io: CoroutineDispatcher,
) {
    @Volatile private var initialized = false

    /** Cache pendek: ekstraksi memakan 1-3 dtk, dan Shorts/unduhan sering meminta video yang sama. URL YouTube berlaku berjam-jam. */
    private class Cached(val at: Long, val value: DevicePlayback)
    private val cache = ConcurrentHashMap<String, Cached>()

    /** [force] = true WAJIB untuk "coba lagi": URL lama mungkin kedaluwarsa/ditolak, jangan pakai cache. */
    suspend fun resolve(videoId: String, force: Boolean = false): Result<DevicePlayback> = withContext(io) {
        if (!force) cache[videoId]?.takeIf { System.currentTimeMillis() - it.at < CACHE_TTL_MS }?.let { return@withContext Result.success(it.value) }
        runCatching {
            ensureInit()
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            val combined = extractor.videoStreams
                .filter { it.isUrl() && !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .sortedBy { it.height }
                .map { StreamOption(it.url ?: it.content, it.resolution ?: "", it.format?.mimeType ?: "video/mp4") }
            val authorId = runCatching {
                extractor.uploaderUrl.orEmpty().substringAfter("/channel/", "").substringBefore("?").substringBefore("/")
            }.getOrDefault("")
            val avatar = runCatching { extractor.uploaderAvatars.maxByOrNull { it.height }?.url }.getOrNull().orEmpty()
            val published = runCatching { extractor.uploadDate?.offsetDateTime()?.toEpochSecond() }.getOrNull() ?: 0L
            val summary = VideoSummary(
                id = videoId,
                title = extractor.name ?: "",
                author = extractor.uploaderName ?: "",
                authorId = authorId,
                authorAvatarUrl = avatar,
                thumbnailUrl = extractor.thumbnails.maxByOrNull { it.height }?.url ?: "",
                lengthSeconds = extractor.length,
                viewCount = extractor.viewCount,
                published = published,
                isLive = extractor.streamType == StreamType.LIVE_STREAM,
            )
            DevicePlayback(
                summary = summary,
                description = runCatching { extractor.description.content }.getOrNull() ?: "",
                combined = combined,
                dashUrl = runCatching { extractor.dashMpdUrl }.getOrNull()?.takeIf { it.isNotBlank() },
                hlsUrl = runCatching { extractor.hlsUrl }.getOrNull()?.takeIf { it.isNotBlank() },
                likeCount = runCatching { extractor.likeCount }.getOrDefault(0L).coerceAtLeast(0L),
                subscriberCount = runCatching { extractor.uploaderSubscriberCount }.getOrDefault(0L).coerceAtLeast(0L),
            ).also { cache[videoId] = Cached(System.currentTimeMillis(), it) }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    private fun ensureInit() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    // Lokalisasi Indonesia: judul/metadata & wilayah konsisten dengan beranda (REGION=ID).
                    NewPipe.init(downloader, Localization("id", "ID"), ContentCountry("ID"))
                    initialized = true
                }
            }
        }
    }

    private companion object { const val CACHE_TTL_MS = 20 * 60 * 1000L }
}
