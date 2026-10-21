package com.app.youtubego.domain.usecase

import com.app.youtubego.domain.model.Keywords
import com.app.youtubego.domain.model.TopicCatalog
import com.app.youtubego.domain.model.VideoSummary
import com.app.youtubego.domain.repository.HomeCategory
import com.app.youtubego.domain.repository.InterestRepository
import com.app.youtubego.domain.repository.VideoRepository
import com.app.youtubego.presentation.common.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.log10
import kotlin.random.Random

/**
 * Feed beranda "Untuk Anda". Sinyal: topik pilihan + kata kunci dari pencarian/tontonan + channel favorit.
 * Alur: kumpulkan kandidat (pencarian per minat + video terbaru channel favorit) -> beri skor
 * -> buang yang sudah ditonton -> sebar agar tak didominasi satu channel -> sisipkan ~20% video
 * trending sebagai penemuan baru (anti "gelembung filter"). Tanpa sinyal sama sekali: trending biasa.
 */
class GetForYouFeedUseCase @Inject constructor(
    private val repo: VideoRepository,
    private val interests: InterestRepository,
) {
    operator fun invoke(): Flow<UiState<List<VideoSummary>>> = flow {
        emit(UiState.Loading)
        emit(build())
    }

    private data class Q(val text: String, val weight: Double)

    private suspend fun build(): UiState<List<VideoSummary>> = coroutineScope {
        val p = interests.profile.value
        val rnd = Random(System.nanoTime())
        val seeds = p.seeds.mapNotNull(TopicCatalog::byId)
        val trendingJob = async { repo.homeFeed(HomeCategory.ALL, 1) }

        if (seeds.isEmpty() && p.topics.isEmpty() && p.channels.isEmpty()) {
            return@coroutineScope trendingJob.await().toUi()
        }

        val top = p.topics.entries.sortedByDescending { it.value }.take(8)
        val candidates = buildList {
            seeds.forEach { add(Q(it.query, 6.0)) }
            top.forEach { add(Q(it.key, it.value)) }
            if (top.size >= 2) add(Q("${top[0].key} ${top[1].key}", (top[0].value + top[1].value) / 2 * 1.3))
            if (top.size >= 3) add(Q("${top[0].key} ${top[2].key}", (top[0].value + top[2].value) / 2 * 1.2))
        }
        // Jitter bobot: tiap penyegaran memilih campuran minat sedikit berbeda.
        val queries = candidates.distinctBy { it.text }
            .sortedByDescending { it.weight * (0.75 + rnd.nextDouble() * 0.5) }.take(5).map { it.text }
        val channelIds = p.channels.entries.sortedByDescending { it.value.score }.take(2).map { it.key }

        val searchJobs = queries.map { q -> async { repo.search(q, page = rnd.nextInt(1, 3)) } }
        val channelJobs = channelIds.map { id -> async { repo.channelLatest(id) } }
        val results = searchJobs.awaitAll() + channelJobs.awaitAll()
        val trending = trendingJob.await()

        val pool = results.flatMap { it.getOrDefault(emptyList()) }.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (pool.isEmpty() && trending.getOrDefault(emptyList()).isEmpty()) {
            val err = (results + trending).firstNotNullOfOrNull { it.exceptionOrNull() }
            return@coroutineScope if (err != null) UiState.Error(err.toFriendlyMessage()) else UiState.Success(emptyList())
        }

        val unseen = pool.filter { it.id !in p.seen }.ifEmpty { pool }
        val maxTopic = p.topics.values.maxOrNull() ?: 1.0
        val maxChannel = p.channels.values.maxOfOrNull { it.score } ?: 1.0
        val seedKw = seeds.flatMap { it.keywords }.toSet()
        val nowSec = System.currentTimeMillis() / 1000.0

        fun score(v: VideoSummary): Double {
            val tokens = Keywords.extract(v.title).toSet()
            val topicScore = tokens.sumOf { (p.topics[it] ?: 0.0) / maxTopic }.coerceAtMost(3.0)
            val seedScore = tokens.count { it in seedKw }.coerceAtMost(2) * 0.8
            val channelScore = p.channels[v.authorId]?.let { (it.score / maxChannel).coerceAtMost(1.0) * 1.5 } ?: 0.0
            val ageDays = (nowSec - v.published) / 86_400.0
            val fresh = if (v.published > 0) (1.0 - (ageDays / 60.0).coerceIn(0.0, 1.0)) * 0.6 else 0.0
            val popularity = log10(v.viewCount + 1.0) * 0.05
            return topicScore + seedScore + channelScore + fresh + popularity + rnd.nextDouble() * 0.2
        }

        // Tidak ada video yang dibuang: urutan = relevan & belum ditonton -> sisipan penemuan -> sisanya (termasuk yang sudah ditonton).
        val ranked = diversify(unseen.sortedByDescending(::score))
        val rankedIds = ranked.mapTo(HashSet()) { it.id }
        val explore = trending.getOrDefault(emptyList())
            .filter { it.id !in rankedIds }.sortedBy { it.id in p.seen }
        val exploreIds = explore.mapTo(HashSet()) { it.id }
        val watchedLeftovers = pool.filter { it.id !in rankedIds && it.id !in exploreIds }

        val out = ranked.toMutableList()
        // Sisipkan penemuan baru tiap 5 posisi mulai posisi 4; sisanya ditambahkan di belakang.
        var inserted = 0
        for ((i, v) in explore.withIndex()) {
            val at = 4 + i * 5
            if (at <= out.size && inserted == i) { out.add(at, v); inserted++ } else out.add(v)
        }
        out.addAll(watchedLeftovers)
        UiState.Success(out.distinctBy { it.id })
    }

    /** Halaman lanjutan "Untuk Anda": putar kueri dari minat/topik pilihan, lalu katalog topik umum. */
    suspend fun more(page: Int): Result<List<VideoSummary>> {
        val p = interests.profile.value
        val queries = (p.seeds.mapNotNull(TopicCatalog::byId).map { it.query } +
            p.topics.entries.sortedByDescending { it.value }.take(6).map { it.key } +
            TopicCatalog.all.map { it.query }).distinct()
        val idx = (page - 2).coerceAtLeast(0)
        return repo.search(queries[idx % queries.size], page = idx / queries.size + 2)
    }

    /** Maksimal 2 video berurutan dari channel yang sama. */
    private fun diversify(sorted: List<VideoSummary>): List<VideoSummary> {
        val rest = sorted.toMutableList()
        val out = ArrayList<VideoSummary>(rest.size)
        while (rest.isNotEmpty()) {
            val lastTwo = out.takeLast(2).map { it.authorId }
            val idx = rest.indexOfFirst { it.authorId.isBlank() || lastTwo.count { a -> a == it.authorId } < 2 }.let { if (it < 0) 0 else it }
            out += rest.removeAt(idx)
        }
        return out
    }

    private fun <T> Result<T>.toUi(): UiState<T> =
        fold(onSuccess = { UiState.Success(it) }, onFailure = { UiState.Error(it.toFriendlyMessage()) })
}
