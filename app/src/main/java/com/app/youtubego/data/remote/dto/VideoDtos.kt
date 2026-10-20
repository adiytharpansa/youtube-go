package com.app.youtubego.data.remote.dto
import kotlinx.serialization.Serializable
@Serializable
data class ThumbnailDto(val url: String = "", val quality: String = "", val width: Int = 0, val height: Int = 0)
@Serializable
data class AuthorThumbnailDto(val url: String = "", val width: Int = 0, val height: Int = 0)
/** Item campuran dari /search, /trending, /popular, /channels/:id/latest - bedakan lewat [type]. */
@Serializable
data class VideoItemDto(
    val type: String = "video",
    val videoId: String = "",
    val title: String = "",
    val author: String = "",
    val authorId: String = "",
    val authorThumbnails: List<AuthorThumbnailDto> = emptyList(),
    val videoThumbnails: List<ThumbnailDto> = emptyList(),
    val lengthSeconds: Long = 0,
    val viewCount: Long = 0,
    val published: Long = 0,
    val liveNow: Boolean = false,
)
@Serializable
data class FormatStreamDto(val url: String = "", val qualityLabel: String = "", val container: String = "", val type: String = "")
@Serializable
data class AdaptiveFormatDto(val url: String = "", val itag: Int = 0, val type: String = "", val bitrate: Long = 0, val qualityLabel: String = "", val container: String = "")
@Serializable
data class CaptionDto(val label: String = "", val languageCode: String = "", val url: String = "")
@Serializable
data class VideoDetailDto(
    val videoId: String = "",
    val title: String = "",
    val description: String = "",
    val author: String = "",
    val authorId: String = "",
    val authorThumbnails: List<AuthorThumbnailDto> = emptyList(),
    val subCountText: String = "",
    val videoThumbnails: List<ThumbnailDto> = emptyList(),
    val lengthSeconds: Long = 0,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val published: Long = 0,
    val liveNow: Boolean = false,
    val hlsUrl: String? = null,
    val dashUrl: String? = null,
    val formatStreams: List<FormatStreamDto> = emptyList(),
    val adaptiveFormats: List<AdaptiveFormatDto> = emptyList(),
    val captions: List<CaptionDto> = emptyList(),
    val recommendedVideos: List<VideoItemDto> = emptyList(),
)
@Serializable
data class CommentDto(val author: String = "", val authorThumbnails: List<AuthorThumbnailDto> = emptyList(), val content: String = "", val published: Long = 0, val likeCount: Long = 0)
@Serializable
data class CommentsDto(val commentCount: Long = 0, val comments: List<CommentDto> = emptyList(), val continuation: String? = null)
@Serializable
data class SearchSuggestionsDto(val query: String = "", val suggestions: List<String> = emptyList())
@Serializable
data class ApiErrorDto(val error: String = "")
@Serializable
data class ChannelDto(
    val author: String = "", val authorId: String = "",
    val authorThumbnails: List<AuthorThumbnailDto> = emptyList(),
    val authorBanners: List<AuthorThumbnailDto> = emptyList(),
    val subCount: Long = 0, val subCountText: String = "", val description: String = "",
    val latestVideos: List<VideoItemDto> = emptyList(),
)
@Serializable
data class PlaylistDto(val title: String = "", val playlistId: String = "", val author: String = "", val videoCount: Int = 0, val videos: List<VideoItemDto> = emptyList())
/** Hasil /search campuran: video/playlist/channel dalam satu array - pisahkan lewat [type]. */
@Serializable
data class SearchItemDto(
    val type: String = "video", val videoId: String = "", val playlistId: String = "",
    val title: String = "", val author: String = "", val authorId: String = "",
    val videoThumbnails: List<ThumbnailDto> = emptyList(),
    val lengthSeconds: Long = 0, val viewCount: Long = 0, val published: Long = 0, val liveNow: Boolean = false,
)
