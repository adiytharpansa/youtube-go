package com.app.youtubego.data.repository
import com.app.youtubego.data.mapper.VideoMapper.toDomain
import com.app.youtubego.data.mapper.VideoMapper.toSummary
import com.app.youtubego.data.remote.InvidiousApi
import com.app.youtubego.domain.model.*
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.domain.repository.VideoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
class VideoRepositoryImpl @Inject constructor(
    private val api: InvidiousApi,
    @Named("io") private val io: CoroutineDispatcher,
) : VideoRepository {
    override suspend fun trending(category: HomeCategory): Result<List<VideoSummary>> = runIo {
        when {
            category.searchFallback != null -> api.search(category.searchFallback, type = "video").filter { it.type == "video" }.map { it.toSummary() }
            category == HomeCategory.ALL || category == HomeCategory.FOR_YOU -> {
                val t = runCatching { api.trending(null) }.getOrDefault(emptyList())
                (if (t.isNotEmpty()) t else api.popular()).map { it.toSummary() }
            }
            else -> api.trending(category.apiType).map { it.toSummary() }
        }
    }
    override suspend fun search(query: String, page: Int, shortsOnly: Boolean): Result<List<VideoSummary>> = runIo {
        api.search(query, page = page, duration = if (shortsOnly) "short" else null)
            .filter { it.type == "video" && it.videoId.isNotBlank() }.map { it.toSummary() }
    }
    override suspend fun suggestions(query: String): Result<List<String>> = runIo { api.suggestions(query).suggestions }
    override suspend fun videoDetail(id: String): Result<VideoDetail> = runIo { api.videoDetail(id, local = true).toDomain() }
    override suspend fun comments(id: String): Result<List<CommentItem>> = runIo {
        api.comments(id).comments.map { CommentItem(it.author, com.app.youtubego.data.mapper.UrlRewriter.rewrite(it.authorThumbnails.lastOrNull()?.url), it.content, it.published, it.likeCount) }
    }
    override suspend fun channel(ucid: String): Result<ChannelInfo> = runIo {
        val d = api.channel(ucid)
        ChannelInfo(ucid, d.author, com.app.youtubego.data.mapper.VideoMapper.avatar(d.authorThumbnails),
            com.app.youtubego.data.mapper.UrlRewriter.rewrite(d.authorBanners.lastOrNull()?.url),
            d.subCountText, d.description, d.latestVideos.map { it.toSummary() })
    }
    override suspend fun channelLatest(ucid: String): Result<List<VideoSummary>> = runIo { api.channelLatest(ucid).map { it.toSummary() } }

    override suspend fun homeFeed(category: HomeCategory, page: Int): Result<List<VideoSummary>> = runIo {
        if (page <= 1) firstPage(category) else morePage(category, page)
    }

    /** Halaman 1: gabungkan SEMUA sumber yang relevan supaya beranda tidak hanya berisi satu daftar trending pendek. */
    private suspend fun firstPage(category: HomeCategory): List<VideoSummary> = coroutineScope {
        val fb = category.searchFallback
        val results: List<Result<List<VideoSummary>>> = when {
            fb != null -> listOf(1, 2).map { pg ->
                async { runCatching { searchVideoItems(fb, pg) } }
            }
            category == HomeCategory.ALL || category == HomeCategory.FOR_YOU -> listOf(
                async { runCatching { api.trending(null).map { it.toSummary() } } },
                async { runCatching { api.popular().map { it.toSummary() } } },
                async { runCatching { api.trending("music").map { it.toSummary() } } },
                async { runCatching { api.trending("gaming").map { it.toSummary() } } },
            )
            else -> listOf(
                async { runCatching { api.trending(category.apiType).map { it.toSummary() } } },
                async { runCatching { searchVideoItems(category.moreQueries.firstOrNull() ?: category.apiType.orEmpty(), 1) } },
            )
        }.awaitAll()
        val merged = results.flatMap { it.getOrDefault(emptyList()) }.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (merged.isEmpty()) results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        merged
    }

    /** Halaman >= 2: putar daftar kueri per kategori; tiap putaran penuh naik satu halaman hasil pencarian. */
    private suspend fun morePage(category: HomeCategory, page: Int): List<VideoSummary> {
        val queries = category.moreQueries.ifEmpty { TopicCatalog.all.map { it.query } }
        val idx = page - 2
        return searchVideoItems(queries[idx % queries.size], idx / queries.size + 2)
    }

    private suspend fun searchVideoItems(query: String, page: Int): List<VideoSummary> =
        api.search(query, page = page).filter { it.type == "video" && it.videoId.isNotBlank() }.map { it.toSummary() }

    private suspend fun <T> runIo(block: suspend () -> T): Result<T> =
        withContext(io) { runCatching { block() } }
}
