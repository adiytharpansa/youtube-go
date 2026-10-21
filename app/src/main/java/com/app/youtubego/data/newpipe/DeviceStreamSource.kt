package com.app.youtubego.data.newpipe

import android.util.Log
import com.app.youtubego.data.innertube.InnertubeClient
import com.app.youtubego.domain.model.CaptionTrack
import com.app.youtubego.domain.model.Chapter
import com.app.youtubego.domain.model.Storyboard
import com.app.youtubego.domain.model.StreamOption
import com.app.youtubego.domain.model.VideoSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
    val storyboard: Storyboard? = null,
    val chapters: List<Chapter> = emptyList(),
    /** Stream video-saja (tanpa audio), terurut kualitas menaik; dipakai Shorts untuk 720p+ lewat penggabungan dengan [audioOnly]. */
    val videoOnly: List<StreamOption> = emptyList(),
    /** Stream audio-saja, terurut bitrate menaik. */
    val audioOnly: List<StreamOption> = emptyList(),
    /** Takarir bawaan jalur sendiri (jalur NewPipe: kosong, takarir datang dari detail). */
    val captions: List<CaptionTrack> = emptyList(),
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

    // ---- Jalur utama buatan sendiri (1 permintaan POST) dengan NewPipeExtractor sebagai cadangan otomatis ----
    private val own = InnertubeClient { url, headers, body -> downloader.postJson(url, headers, body) }
    /** Video yang pemutarannya gagal lewat jalur sendiri: dipaksa ke NewPipe. */
    private val noOwn = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var ownOffUntil = 0L
    private val ownFails = AtomicInteger(0)
    private val playFails = AtomicInteger(0)

    private fun ownEnabled(id: String) = id !in noOwn && System.currentTimeMillis() >= ownOffUntil

    /**
     * Dipanggil saat pemutaran dari jalur sendiri gagal (mis. 403): video ini beralih ke NewPipe. Tiga kegagalan
     * berturut-turut mematikan jalur sendiri 30 menit (YouTube mungkin menutup klien itu), lalu dicoba lagi otomatis.
     */
    fun avoidOwn(videoId: String) {
        if (noOwn.size > 200) noOwn.clear()
        noOwn.add(videoId)
        if (playFails.incrementAndGet() >= 3) { ownOffUntil = System.currentTimeMillis() + 30 * 60_000L; playFails.set(0); Log.w(TAG, "jalur sendiri dimatikan 30 menit") }
        invalidate(videoId)
    }

    /** Scope sendiri: ekstraksi yang sudah berjalan TIDAK ikut batal saat satu pemanggil (layar) batal; hasilnya tetap dipakai pemanggil berikutnya. */
    private val scope = CoroutineScope(SupervisorJob() + io)

    private class Entry<T>(val at: Long, val job: Deferred<T>)
    /** Satu unduhan halaman tonton per video (dipakai bersama pemutar, detail, Shorts, unduhan, dan prefetch). */
    private val extractors = ConcurrentHashMap<String, Entry<StreamExtractor>>()
    /** Hasil ekstraksi (DevicePlayback). Permintaan serentak untuk video yang sama menunggu pekerjaan yang SAMA (tidak ekstraksi ganda). */
    private val playbacks = ConcurrentHashMap<String, Entry<DevicePlayback>>()

    private fun <T> fresh(map: ConcurrentHashMap<String, Entry<T>>, id: String, start: () -> Deferred<T>): Entry<T> {
        val now = System.currentTimeMillis()
        val e = map.compute(id) { _, old ->
            if (old != null && now - old.at < CACHE_TTL_MS && !old.job.isCancelled) old else Entry(now, start())
        }!!
        if (map.size > MAX_ENTRIES) map.entries.filter { it.key != id }.minByOrNull { it.value.at }?.let { map.remove(it.key) }
        return e
    }

    /** Buang cache video ini (URL lama kedaluwarsa/ditolak): pemanggilan berikutnya mengambil data segar. */
    fun invalidate(videoId: String) { extractors.remove(videoId); playbacks.remove(videoId) }

    /** Extractor yang SUDAH fetchPage() untuk video ini; dipakai bersama agar halaman tonton hanya diunduh sekali. */
    suspend fun sharedExtractor(videoId: String): StreamExtractor {
        val e = fresh(extractors, videoId) {
            scope.async {
                ensureInit()
                ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId").also { it.fetchPage() }
            }
        }
        return try { e.job.await() } catch (t: Throwable) { extractors.remove(videoId, e); throw t }
    }

    /** Mulai ekstraksi di latar belakang (hasil masuk cache). Dipakai untuk video yang HAMPIR PASTI diputar berikutnya. */
    fun prefetch(videoId: String) {
        if (videoId.isBlank()) return
        scope.launch { runCatching { resolve(videoId) } }
    }

    /** Siapkan NewPipe + koneksi TLS ke YouTube sebelum pengguna menekan video pertama. */
    fun warmUp() {
        scope.launch {
            runCatching { ensureInit() }
            kotlinx.coroutines.delay(700)   // beri CPU ke frame pertama dulu; handshake TLS menyusul
            runCatching { downloader.warmUp() }
        }
    }

    /** [force] = true WAJIB untuk "coba lagi": URL lama mungkin kedaluwarsa/ditolak, jangan pakai cache. */
    suspend fun resolve(videoId: String, force: Boolean = false): Result<DevicePlayback> {
        if (force) avoidOwn(videoId)   // "coba lagi" = jalur sebelumnya gagal: pakai cadangan NewPipe
        val cachedHit = playbacks[videoId]?.takeIf { it.job.isCompleted && !it.job.isCancelled && System.currentTimeMillis() - it.at < CACHE_TTL_MS }
        if (cachedHit != null) Log.d(TAG, "ekstraksi $videoId: dari cache")
        val e = fresh(playbacks, videoId) { scope.async { extract(videoId) } }
        return try {
            Result.success(e.job.await()).also {
                if (cachedHit == null) { val v = it.getOrNull()!!; Log.d(TAG, "ekstraksi $videoId OK: gabungan=${v.combined.size} videoOnly=${v.videoOnly.size} audioOnly=${v.audioOnly.size} dash=${v.dashUrl != null} hls=${v.hlsUrl != null}") }
            }
        } catch (t: Throwable) {
            if (t is CancellationException && e.job.isActive) throw t   // pemanggil dibatalkan, ekstraksi tetap jalan di scope
            playbacks.remove(videoId, e)
            if (t is CancellationException) throw t
            Log.w(TAG, "ekstraksi $videoId GAGAL: ${t.javaClass.simpleName}: ${t.message}")
            Result.failure(t)
        }
    }

    private suspend fun extract(videoId: String): DevicePlayback {
        if (ownEnabled(videoId)) {
            val r = runCatching { own.fetch(videoId) }
            val o = r.getOrNull()
            if (o != null) {
                ownFails.set(0)
                Log.d(TAG, "jalur sendiri OK $videoId: gabungan=${o.combined.size} videoOnly=${o.videoOnly.size} audioOnly=${o.audioOnly.size}")
                return DevicePlayback(o.summary, o.description, o.combined, null, null, videoOnly = o.videoOnly, audioOnly = o.audioOnly, captions = o.captions)
            }
            Log.w(TAG, "jalur sendiri gagal $videoId: ${r.exceptionOrNull()?.message} -> NewPipe")
            if (ownFails.incrementAndGet() >= 3) { ownOffUntil = System.currentTimeMillis() + 15 * 60_000L; ownFails.set(0) }
        }
        return extractNewPipe(videoId)
    }

    /** Storyboard + bab dari extractor NewPipe (dibagi dengan detail); dipakai melengkapi hasil jalur sendiri. */
    suspend fun extras(videoId: String): Pair<Storyboard?, List<Chapter>> {
        val ex = sharedExtractor(videoId)
        return synchronized(ex) { storyboardOf(ex) to chaptersOf(ex) }
    }

    private fun storyboardOf(extractor: StreamExtractor): Storyboard? = runCatching {
        extractor.frames.filter { it.urls.isNotEmpty() && it.frameWidth > 0 && it.totalCount > 0 && it.durationPerFrame > 0 }
            .minByOrNull { kotlin.math.abs(it.frameWidth - 160) }
            ?.let { Storyboard(it.urls, it.frameWidth, it.frameHeight, it.totalCount, it.durationPerFrame, it.framesPerPageX, it.framesPerPageY) }
    }.getOrNull()

    private fun chaptersOf(extractor: StreamExtractor): List<Chapter> = runCatching {
        extractor.streamSegments.map { Chapter(it.title ?: "", it.startTimeSeconds * 1000L) }.sortedBy { it.startMs }
    }.getOrDefault(emptyList())

    private suspend fun extractNewPipe(videoId: String): DevicePlayback {
        val extractor = sharedExtractor(videoId)
        // Getter extractor tidak aman dipakai dua thread sekaligus; pemutar & detail berbagi objek yang sama.
        return synchronized(extractor) {
            val combined = extractor.videoStreams
                .filter { it.isUrl() && !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .sortedBy { it.height }
                .map { StreamOption(it.url ?: it.content, it.resolution ?: "", it.format?.mimeType ?: "video/mp4") }
            // Video-saja + audio-saja: kualitas di atas 360p (stream gabungan YouTube umumnya mentok 360p). Terbaik-upaya.
            val videoOnly = runCatching {
                extractor.videoOnlyStreams
                    .filter { it.isUrl() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .sortedBy { v -> v.resolution?.takeWhile { c -> c.isDigit() }?.toIntOrNull() ?: v.height }
                    .map { StreamOption(it.url ?: it.content, it.resolution ?: "", it.format?.mimeType ?: "video/mp4", it.codec.orEmpty()) }
            }.getOrDefault(emptyList())
            val audioOnly = runCatching {
                extractor.audioStreams
                    .filter { it.isUrl() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .sortedBy { it.averageBitrate }
                    .map { StreamOption(it.url ?: it.content, "${it.averageBitrate}", it.format?.mimeType ?: "audio/mp4") }
            }.getOrDefault(emptyList())
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
                // Pratinjau seekbar & bab: terbaik-upaya, gagal = fitur sekadar tidak tampil (tidak boleh menggagalkan putar).
                storyboard = storyboardOf(extractor),
                chapters = chaptersOf(extractor),
                videoOnly = videoOnly,
                audioOnly = audioOnly,
            )
        }
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

    private companion object { const val CACHE_TTL_MS = 20 * 60 * 1000L; const val MAX_ENTRIES = 16; const val TAG = "YGO_PLAYER" }
}
