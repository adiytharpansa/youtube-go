package com.app.youtubego.domain.model
/** Model domain: bersih dari anotasi jaringan; semua data diekstrak di perangkat. */
data class VideoSummary(
    val id: String, val title: String, val author: String, val authorId: String,
    val authorAvatarUrl: String, val thumbnailUrl: String,
    val lengthSeconds: Long, val viewCount: Long, val published: Long, val isLive: Boolean,
)
/** Sprite pratinjau seekbar (storyboard YouTube): tiap gambar berisi [perX] x [perY] bingkai berukuran [frameW] x [frameH]. */
data class Storyboard(
    val urls: List<String>, val frameW: Int, val frameH: Int, val total: Int,
    val durationPerFrameMs: Int, val perX: Int, val perY: Int,
)
/** Bab video (penanda waktu di deskripsi). */
data class Chapter(val title: String, val startMs: Long)
data class StreamOption(val url: String, val qualityLabel: String, val mimeType: String)
data class CaptionTrack(val label: String, val languageCode: String, val url: String)
data class VideoDetail(
    val summary: VideoSummary, val description: String, val likeCount: Long,
    val subscriberText: String,
    /** Stream gabungan progresif (video+audio) dari ekstraksi perangkat, mulai kualitas terendah yang layak. */
    val combinedStreams: List<StreamOption>,
    /** URL manifes DASH dari ekstraksi perangkat bila ada - jalur adaptif. */
    val dashUrl: String?, val hlsUrl: String?,
    val captions: List<CaptionTrack>, val recommendations: List<VideoSummary>,
)
data class CommentItem(val author: String, val avatarUrl: String, val content: String, val published: Long, val likeCount: Long)
data class ChannelInfo(val id: String, val name: String, val avatarUrl: String, val bannerUrl: String, val subscriberText: String, val description: String, val latest: List<VideoSummary>)

/** Playlist buatan pengguna, tersimpan lokal di perangkat. [videoIds] urut dari yang terbaru ditambahkan. */
data class Playlist(val id: String, val name: String, val videoIds: List<String> = emptyList())

/** Hasil channel pada pencarian. [id] berupa UC... atau @handle (sama dengan yang dipakai ChannelScreen). */
data class ChannelResult(val id: String, val name: String, val avatarUrl: String, val subscriberText: String)
