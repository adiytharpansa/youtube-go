package com.app.youtubego.data.newpipe

import com.app.youtubego.domain.model.StreamOption
import com.app.youtubego.domain.model.VideoSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamType
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

    suspend fun resolve(videoId: String): Result<DevicePlayback> = withContext(io) {
        runCatching {
            ensureInit()
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            val combined = extractor.videoStreams
                .filter { it.isUrl() && !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .sortedBy { it.height }
                .map { StreamOption(it.url ?: it.content, it.resolution ?: "", it.format?.mimeType ?: "video/mp4") }
            val summary = VideoSummary(
                id = videoId,
                title = extractor.name ?: "",
                author = extractor.uploaderName ?: "",
                authorId = "",
                authorAvatarUrl = "",
                thumbnailUrl = extractor.thumbnails.maxByOrNull { it.height }?.url ?: "",
                lengthSeconds = extractor.length,
                viewCount = extractor.viewCount,
                published = 0L,
                isLive = extractor.streamType == StreamType.LIVE_STREAM,
            )
            DevicePlayback(
                summary = summary,
                description = extractor.textualDescription ?: "",
                combined = combined,
                dashUrl = runCatching { extractor.dashMpdUrl }.getOrNull()?.takeIf { it.isNotBlank() },
                hlsUrl = runCatching { extractor.hlsUrl }.getOrNull()?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun ensureInit() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(downloader)
                    initialized = true
                }
            }
        }
    }
}
