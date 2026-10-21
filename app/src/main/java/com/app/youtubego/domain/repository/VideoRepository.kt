package com.app.youtubego.domain.repository
import com.app.youtubego.domain.model.*
interface VideoRepository {
    suspend fun trending(category: HomeCategory): Result<List<VideoSummary>>
    suspend fun search(query: String, page: Int = 1, shortsOnly: Boolean = false): Result<List<VideoSummary>>
    /** Channel yang cocok dengan kueri (diambil dari halaman pertama hasil pencarian yang sama; tanpa permintaan jaringan tambahan). */
    suspend fun searchChannels(query: String): Result<List<ChannelResult>>
    suspend fun suggestions(query: String): Result<List<String>>
    suspend fun videoDetail(id: String): Result<VideoDetail>
    suspend fun comments(id: String): Result<List<CommentItem>>
    suspend fun channel(ucid: String): Result<ChannelInfo>
    suspend fun channelLatest(ucid: String): Result<List<VideoSummary>>
    /** Beranda berhalaman: page 1 = gabungan beberapa sumber, page >= 2 = lanjutan tak terbatas lewat pencarian. */
    suspend fun homeFeed(category: HomeCategory, page: Int): Result<List<VideoSummary>>
}
enum class HomeCategory(val apiType: String?, val searchFallback: String?, val moreQueries: List<String> = emptyList()) {
    FOR_YOU(null, null),
    ALL(null, null),
    MUSIC("music", null, listOf("musik populer terbaru", "lagu hits indonesia terbaru", "cover akustik", "musik santai playlist", "konser live musik")),
    GAMING("gaming", null, listOf("gameplay game populer", "mobile legends", "minecraft indonesia", "game terbaru review", "highlight gaming")),
    MIX(null, "musik mix", listOf("musik mix terbaru", "dj remix", "nonstop mix", "lagu galau mix")),
    NEWS(null, "berita terkini", listOf("berita hari ini", "berita politik terkini", "berita ekonomi", "berita olahraga terkini")),
    LEARN(null, "belajar", listOf("tutorial belajar", "belajar pemrograman", "pelajaran sekolah", "tips belajar efektif", "sains fakta menarik")),
}
