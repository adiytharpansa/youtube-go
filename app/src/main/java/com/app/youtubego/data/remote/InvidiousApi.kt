package com.app.youtubego.data.remote
import com.app.youtubego.data.remote.dto.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
/**
 * Retrofit interface - 1:1 dengan dokumen Invidious API (publik, tanpa login).
 * Grup endpoint auth SENGAJA tidak dipasang: aplikasi memakai akun lokal seperti web app,
 * API publik tidak menyediakan login pengguna akhir yang aman untuk aplikasi ini.
 */
interface InvidiousApi {
    // Feed
    @GET("api/v1/trending")
    suspend fun trending(@Query("type") type: String? = null, @Query("region") region: String = ApiConstants.REGION): List<VideoItemDto>
    @GET("api/v1/popular")
    suspend fun popular(): List<VideoItemDto>
    // Video
    @GET("api/v1/videos/{id}")
    suspend fun videoDetail(@Path("id") id: String, @Query("local") local: Boolean = true, @Query("region") region: String = ApiConstants.REGION): VideoDetailDto
    @GET("api/v1/comments/{id}")
    suspend fun comments(@Path("id") id: String, @Query("continuation") continuation: String? = null, @Query("sort_by") sortBy: String = "top"): CommentsDto
    @GET("api/v1/captions/{id}")
    suspend fun captions(@Path("id") id: String): CaptionListHolder
    // Pencarian
    @GET("api/v1/search")
    suspend fun search(@Query("q") q: String, @Query("page") page: Int = 1, @Query("type") type: String = "video", @Query("duration") duration: String? = null, @Query("region") region: String = ApiConstants.REGION): List<SearchItemDto>
    @GET("api/v1/search/suggestions")
    suspend fun suggestions(@Query("q") q: String): SearchSuggestionsDto
    // Channel & Playlist
    @GET("api/v1/channels/{ucid}")
    suspend fun channel(@Path("ucid") ucid: String): ChannelDto
    @GET("api/v1/channels/{ucid}/latest")
    suspend fun channelLatest(@Path("ucid") ucid: String): List<VideoItemDto>
    @GET("api/v1/playlists/{plid}")
    suspend fun playlist(@Path("plid") plid: String): PlaylistDto
    @GET("api/v1/stats")
    suspend fun stats(): kotlinx.serialization.json.JsonObject
}
@kotlinx.serialization.Serializable
data class CaptionListHolder(val captions: List<CaptionDto> = emptyList())
/** Hasil /search campuran: video/playlist/channel dalam satu array - pisahkan lewat [type]. */
@kotlinx.serialization.Serializable
data class SearchItemDto(
    val type: String = "video", val videoId: String = "", val playlistId: String = "",
    val title: String = "", val author: String = "", val authorId: String = "",
    val videoThumbnails: List<ThumbnailDto> = emptyList(),
    val lengthSeconds: Long = 0, val viewCount: Long = 0, val published: Long = 0, val liveNow: Boolean = false,
)
