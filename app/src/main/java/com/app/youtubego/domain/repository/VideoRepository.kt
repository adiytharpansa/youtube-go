package com.app.youtubego.domain.repository
import com.app.youtubego.domain.model.*
interface VideoRepository {
    suspend fun trending(category: HomeCategory): Result<List<VideoSummary>>
    suspend fun search(query: String, page: Int = 1, shortsOnly: Boolean = false): Result<List<VideoSummary>>
    suspend fun suggestions(query: String): Result<List<String>>
    suspend fun videoDetail(id: String): Result<VideoDetail>
    suspend fun comments(id: String): Result<List<CommentItem>>
    suspend fun channel(ucid: String): Result<ChannelInfo>
    suspend fun channelLatest(ucid: String): Result<List<VideoSummary>>
}
enum class HomeCategory(val apiType: String?, val searchFallback: String?) {
    ALL(null, null), MUSIC("music", null), GAMING("gaming", null),
    MIX(null, "musik mix"), NEWS(null, "berita terkini"), LEARN(null, "belajar")
}
