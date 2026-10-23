package com.app.playtubelite.data.newpipe

import com.app.playtubelite.domain.model.CaptionTrack
import com.app.playtubelite.domain.model.ChannelInfo
import com.app.playtubelite.domain.model.ChannelResult
import com.app.playtubelite.domain.model.CommentItem
import com.app.playtubelite.domain.model.StreamOption
import com.app.playtubelite.domain.model.TopicCatalog
import com.app.playtubelite.domain.model.VideoDetail
import com.app.playtubelite.domain.model.VideoSummary
import com.app.playtubelite.domain.repository.HomeCategory
import com.app.playtubelite.domain.repository.VideoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Satu-satunya sumber data aplikasi: NewPipeExtractor langsung ke YouTube dari perangkat.
 * Tanpa server perantara: beranda, pencarian, saran, detail, komentar, dan channel semuanya diekstrak di HP,
 * berangkat dari IP pengguna — jalur yang sama dengan putar.
 */
@Singleton
class NewPipeVideoRepository @Inject constructor(
    private val downloader: OkHttpNewPipeDownloader,
    private val deviceSource: DeviceStreamSource,
    @Named("io") private val io: CoroutineDispatcher,
) : VideoRepository {

    @Volatile private var initialized = false

    private fun ensureInit() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(downloader, Localization("id", "ID"), ContentCountry("ID"))
                    initialized = true
                }
            }
        }
    }

    // ---------- pemetaan ----------

    private fun idFromWatchUrl(url: String): String =
        url.substringAfter("watch?v=", "").substringBefore("&").ifBlank {
            url.substringAfter("/shorts/", "").substringBefore("?").substringBefore("/")
                .ifBlank { url.substringAfterLast("/").substringBefore("?") }
        }

    /** Uploader bisa berupa /channel/UC... atau /@handle; keduanya dipertahankan apa adanya. */
    private fun channelKeyFromUrl(url: String): String {
        val byChannel = url.substringAfter("/channel/", "").substringBefore("?").substringBefore("/")
        if (byChannel.isNotBlank()) return byChannel
        val byHandle = url.substringAfter("/@", "").substringBefore("?").substringBefore("/")
        return if (byHandle.isNotBlank()) "@$byHandle" else ""
    }

    private fun channelUrl(key: String): String =
        if (key.startsWith("@")) "https://www.youtube.com/$key" else "https://www.youtube.com/channel/$key"

    private fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    /** Pilih thumbnail ~400px tinggi (cukup tajam untuk kartu, jauh lebih kecil dari maxres); cadangan: terbesar yang ada. */
    private fun List<org.schabi.newpipe.extractor.Image>.pickThumbnail(): String =
        filter { it.height in 150..500 }.minByOrNull { kotlin.math.abs(it.height - 400) }?.url
            ?: maxByOrNull { it.height }?.url ?: ""

    private fun StreamInfoItem.toSummary(): VideoSummary = VideoSummary(
        id = idFromWatchUrl(url),
        title = name ?: "",
        author = uploaderName ?: "",
        authorId = channelKeyFromUrl(uploaderUrl ?: ""),
        authorAvatarUrl = uploaderAvatars.maxByOrNull { it.height }?.url ?: "",
        thumbnailUrl = thumbnails.pickThumbnail(),
        lengthSeconds = duration,
        viewCount = viewCount,
        published = runCatching { uploadDate?.offsetDateTime()?.toEpochSecond() }.getOrNull() ?: 0L,
        isLive = streamType == StreamType.LIVE_STREAM,
    )

    private fun List<InfoItem>.summaries(): List<VideoSummary> =
        // distinctBy: daftar Lazy memakai id sebagai key; id ganda (YouTube kadang mengulang video) membuat aplikasi crash.
        filterIsInstance<StreamInfoItem>().map { it.toSummary() }.filter { it.id.isNotBlank() }.distinctBy { it.id }

    private fun List<InfoItem>.channelResults(): List<ChannelResult> =
        filterIsInstance<ChannelInfoItem>().map {
            ChannelResult(
                id = channelKeyFromUrl(it.url ?: ""),
                name = it.name ?: "",
                avatarUrl = it.thumbnails.maxByOrNull { img -> img.height }?.url ?: "",
                subscriberText = subscriberText(it.subscriberCount),
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }

    private fun formatCountId(n: Long): String {
        fun f(v: Double, unit: String) =
            String.format(Locale("id", "ID"), "%.1f", v).removeSuffix(",0") + " $unit"
        return when {
            n >= 1_000_000_000 -> f(n / 1e9, "M")
            n >= 1_000_000 -> f(n / 1e6, "jt")
            n >= 1_000 -> f(n / 1e3, "rb")
            else -> n.toString()
        }
    }

    private fun subscriberText(count: Long): String =
        if (count > 0) "${formatCountId(count)} subscriber" else ""

    // ---------- pencarian berhalaman (sesi per kueri) ----------

    private inner class SearchSession(private val query: String) {
        private var extractor: SearchExtractor? = null
        private val pages = mutableListOf<List<VideoSummary>>()
        private var nextPage: Page? = null
        private var started = false
        private var exhausted = false
        /** Channel yang muncul di halaman pertama hasil (YouTube menyisipkannya di antara video). */
        @Volatile var channels: List<ChannelResult> = emptyList()

        @Synchronized
        fun page(n: Int): List<VideoSummary> {
            ensureInit()
            while (pages.size < n && !exhausted) {
                val ex = extractor ?: ServiceList.YouTube.getSearchExtractor(query).also { extractor = it }
                if (!started) { ex.fetchPage(); started = true }
                val result: ListExtractor.InfoItemsPage<InfoItem> =
                    if (nextPage == null && pages.isEmpty()) ex.initialPage else ex.getPage(nextPage)
                if (pages.isEmpty()) channels = result.items.channelResults()
                pages += result.items.summaries()
                nextPage = if (result.hasNextPage()) result.nextPage else null
                if (nextPage == null) exhausted = true
            }
            return pages.getOrNull(n - 1) ?: emptyList()
        }
    }

    private val sessions = ConcurrentHashMap<String, SearchSession>()

    private fun searchItems(query: String, page: Int, shortsOnly: Boolean): List<VideoSummary> {
        if (sessions.size > 24) sessions.clear()
        val session = sessions.computeIfAbsent("$query|${if (shortsOnly) 1 else 0}") { SearchSession(query) }
        val items = session.page(page)
        return if (shortsOnly) items.filter { it.lengthSeconds in 1..61 } else items
    }

    // ---------- VideoRepository ----------

    override suspend fun trending(category: HomeCategory): Result<List<VideoSummary>> = runIo {
        when (category) {
            HomeCategory.ALL, HomeCategory.FOR_YOU -> kioskItems()
            else -> searchItems(category.moreQueries.firstOrNull() ?: category.searchFallback ?: "video populer", 1, false)
        }
    }

    private fun kioskItems(): List<VideoSummary> {
        ensureInit()
        val kiosk = ServiceList.YouTube.getKioskList().defaultKioskExtractor
        kiosk.fetchPage()
        return kiosk.initialPage.items.summaries()
    }

    override suspend fun homeFeed(category: HomeCategory, page: Int): Result<List<VideoSummary>> = runIo {
        if (page <= 1) firstPage(category) else morePage(category, page)
    }

    /** Halaman 1: gabungkan beberapa sumber di perangkat supaya beranda penuh sejak awal. */
    private suspend fun firstPage(category: HomeCategory): List<VideoSummary> = coroutineScope {
        val legs: List<Pair<String, suspend () -> List<VideoSummary>>> = when {
            category.searchFallback != null -> listOf(
                "fb1" to { searchItems(category.searchFallback, 1, false) },
                "fb2" to { searchItems(category.searchFallback, 2, false) },
            )
            category == HomeCategory.ALL || category == HomeCategory.FOR_YOU -> listOf(
                "kiosk" to { kioskItems() },
                "musik" to { searchItems("musik populer terbaru", 1, false) },
                "game" to { searchItems("gameplay game populer", 1, false) },
            )
            else -> listOf(
                "q1" to { searchItems(category.moreQueries.firstOrNull() ?: "video populer", 1, false) },
                "q2" to { searchItems(category.moreQueries.getOrNull(1) ?: category.moreQueries.firstOrNull() ?: "video populer", 1, false) },
            )
        }
        val results = legs.map { (_, leg) -> async { runCatching { leg() } } }.awaitAll()
        val merged = results.flatMap { it.getOrDefault(emptyList()) }.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (merged.isEmpty()) {
            results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
            throw IllegalStateException("Beranda kosong")
        }
        merged
    }

    /** Halaman >= 2: putar daftar kueri per kategori; tiap putaran penuh naik satu halaman hasil. */
    private fun morePage(category: HomeCategory, page: Int): List<VideoSummary> {
        val queries = category.moreQueries.ifEmpty { TopicCatalog.all.map { it.query } }
        val idx = page - 2
        return searchItems(queries[idx % queries.size], idx / queries.size + 2, false)
    }

    override suspend fun search(query: String, page: Int, shortsOnly: Boolean): Result<List<VideoSummary>> = runIo {
        searchItems(query, page, shortsOnly)
    }

    override suspend fun searchChannels(query: String): Result<List<ChannelResult>> = runIo {
        if (sessions.size > 24) sessions.clear()
        val session = sessions.computeIfAbsent("$query|0") { SearchSession(query) }
        session.page(1)
        session.channels
    }

    override suspend fun suggestions(query: String): Result<List<String>> = runIo {
        ensureInit()
        ServiceList.YouTube.getSuggestionExtractor().suggestionList(query)
    }

    override suspend fun videoDetail(id: String): Result<VideoDetail> = runIo {
        // Halaman tonton diunduh SEKALI dan dibagi dengan pemutar (DeviceStreamSource); dulu diunduh dua kali paralel.
        val (extractor, dev) = coroutineScope {
            val e = async { deviceSource.sharedExtractor(id) }
            val d = async { deviceSource.resolve(id).getOrNull() }
            e.await() to d.await()
        }
        synchronized(extractor) { buildDetail(id, extractor, dev) }
    }

    private fun buildDetail(id: String, extractor: org.schabi.newpipe.extractor.stream.StreamExtractor, dev: DevicePlayback?): VideoDetail {
        val summary = VideoSummary(
            id = id,
            title = extractor.name ?: "",
            author = extractor.uploaderName ?: "",
            authorId = channelKeyFromUrl(extractor.uploaderUrl ?: ""),
            authorAvatarUrl = extractor.uploaderAvatars.maxByOrNull { it.height }?.url ?: "",
            thumbnailUrl = extractor.thumbnails.maxByOrNull { it.height }?.url ?: "",
            lengthSeconds = extractor.length,
            viewCount = extractor.viewCount,
            published = runCatching { extractor.uploadDate?.offsetDateTime()?.toEpochSecond() }.getOrNull() ?: 0L,
            isLive = extractor.streamType == StreamType.LIVE_STREAM,
        )
        return VideoDetail(
            summary = summary,
            description = runCatching { extractor.description.content }.getOrNull() ?: "",
            likeCount = runCatching { extractor.likeCount }.getOrDefault(0L).coerceAtLeast(0L),
            subscriberText = subscriberText(runCatching { extractor.uploaderSubscriberCount }.getOrDefault(0L)),
            // Daftar stream sudah dihitung sekali oleh DeviceStreamSource; jangan urai ulang (mahal: dekode parameter anti-throttle).
            combinedStreams = dev?.combined ?: extractor.videoStreams
                .filter { it.isUrl() && !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .sortedBy { it.height }
                .map { StreamOption(it.url ?: it.content, it.resolution ?: "", it.format?.mimeType ?: "video/mp4") },
            dashUrl = dev?.dashUrl ?: runCatching { extractor.dashMpdUrl }.getOrNull()?.takeIf { it.isNotBlank() },
            hlsUrl = dev?.hlsUrl ?: runCatching { extractor.hlsUrl }.getOrNull()?.takeIf { it.isNotBlank() },
            captions = runCatching { extractor.subtitlesDefault }.getOrDefault(emptyList()).map {
                CaptionTrack(
                    label = it.displayLanguageName.ifBlank { it.languageTag },
                    languageCode = it.languageTag,
                    url = it.content ?: "",
                )
            },
            // YouTube sering mengubah struktur "video terkait": coba API baru (relatedItems) lalu yang lama.
            recommendations = (runCatching { extractor.relatedItems?.items?.summaries() }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?: runCatching { extractor.relatedStreams?.items?.summaries() }.getOrNull()
                ?: emptyList()).filter { it.id != id },
        )
    }

    override suspend fun comments(id: String): Result<List<CommentItem>> = runIo {
        ensureInit()
        val extractor = ServiceList.YouTube.getCommentsExtractor(watchUrl(id))
        extractor.fetchPage()
        // Halaman pertama + satu halaman lanjutan (terbaik-upaya) agar daftar komentar lebih lengkap.
        val raw = ArrayList<CommentsInfoItem>()
        var page = extractor.initialPage
        raw += page.items.filterIsInstance<CommentsInfoItem>()
        if (page.hasNextPage()) {
            runCatching { extractor.getPage(page.nextPage) }.getOrNull()?.let { raw += it.items.filterIsInstance<CommentsInfoItem>() }
        }
        raw.map {
            CommentItem(
                author = it.uploaderName ?: "",
                avatarUrl = it.uploaderAvatars.maxByOrNull { im -> im.height }?.url ?: "",
                content = it.commentText.content,
                published = runCatching { it.uploadDate?.offsetDateTime()?.toEpochSecond() }.getOrNull() ?: 0L,
                likeCount = it.likeCount.toLong(),
            )
        }
    }

    override suspend fun channel(ucid: String): Result<ChannelInfo> = runIo {
        ensureInit()
        val extractor = ServiceList.YouTube.getChannelExtractor(channelUrl(ucid))
        extractor.fetchPage()
        ChannelInfo(
            id = ucid,
            name = extractor.name ?: "",
            avatarUrl = runCatching { extractor.avatars.maxByOrNull { it.height }?.url }.getOrNull() ?: "",
            bannerUrl = runCatching { extractor.banners.maxByOrNull { it.height }?.url }.getOrNull() ?: "",
            subscriberText = subscriberText(runCatching { extractor.subscriberCount }.getOrDefault(0L)),
            description = runCatching { extractor.description }.getOrNull() ?: "",
            latest = runCatching { channelLatestItems(ucid) }.getOrDefault(emptyList()),
        )
    }

    override suspend fun channelLatest(ucid: String): Result<List<VideoSummary>> = runIo {
        channelLatestItems(ucid)
    }

    private fun channelLatestItems(key: String): List<VideoSummary> {
        ensureInit()
        // Parameter pertama adalah ID channel ("UC..." atau "@handle"), BUKAN URL: extractor membangun
        // "https://www.youtube.com/<id>/videos" sendiri. Memberi URL penuh membuat tautan ganda yang tak bisa di-resolve.
        val tab = ServiceList.YouTube.getChannelTabExtractorFromId(key, "videos")
        tab.fetchPage()
        return tab.initialPage.items.summaries()
    }

    private suspend fun <T> runIo(block: suspend () -> T): Result<T> =
        withContext(io) {
            runCatching { block() }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
            }
        }
}
