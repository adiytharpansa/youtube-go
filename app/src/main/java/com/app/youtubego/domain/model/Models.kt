package com.app.youtubego.domain.model
/** Model domain: bersih dari anotasi jaringan, URL sudah ditulis ulang ke proksi. */
data class VideoSummary(
    val id: String, val title: String, val author: String, val authorId: String,
    val authorAvatarUrl: String, val thumbnailUrl: String,
    val lengthSeconds: Long, val viewCount: Long, val published: Long, val isLive: Boolean,
)
data class StreamOption(val url: String, val qualityLabel: String, val mimeType: String)
data class CaptionTrack(val label: String, val languageCode: String, val url: String)
data class VideoDetail(
    val summary: VideoSummary, val description: String, val likeCount: Long,
    val subscriberText: String,
    /** formatStreams gabungan (video+audio) - jalur putar utama hemat data, mulai kualitas terendah yang layak. */
    val combinedStreams: List<StreamOption>,
    /** dashUrl dari API bila ada - jalur adaptif (1080p) tahap berikutnya. */
    val dashUrl: String?, val hlsUrl: String?,
    val captions: List<CaptionTrack>, val recommendations: List<VideoSummary>,
)
data class CommentItem(val author: String, val avatarUrl: String, val content: String, val published: Long, val likeCount: Long)
data class ChannelInfo(val id: String, val name: String, val avatarUrl: String, val bannerUrl: String, val subscriberText: String, val description: String, val latest: List<VideoSummary>)

/** Playlist buatan pengguna, tersimpan lokal di perangkat. [videoIds] urut dari yang terbaru ditambahkan. */
data class Playlist(val id: String, val name: String, val videoIds: List<String> = emptyList())
