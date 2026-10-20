package com.app.youtubego.data.mapper
import com.app.youtubego.data.remote.dto.*
import com.app.youtubego.domain.model.*
object VideoMapper {
    /** Thumbnail terbaik yang BUKAN medium: urutan web yang terverifikasi = high > sd > maxres > default. */
    fun bestThumbnail(thumbs: List<ThumbnailDto>): String {
        val order = listOf("high", "sddefault", "maxresdefault", "maxres", "default")
        val picked = order.firstNotNullOfOrNull { q -> thumbs.firstOrNull { it.quality == q } }
            ?: thumbs.firstOrNull { it.quality != "medium" } ?: thumbs.firstOrNull()
        return UrlRewriter.rewrite(picked?.url)
    }
    fun avatar(thumbs: List<AuthorThumbnailDto>): String = UrlRewriter.rewrite(thumbs.lastOrNull()?.url)
    fun VideoItemDto.toSummary() = VideoSummary(
        id = videoId, title = title, author = author, authorId = authorId,
        authorAvatarUrl = avatar(authorThumbnails), thumbnailUrl = bestThumbnail(videoThumbnails),
        lengthSeconds = lengthSeconds, viewCount = viewCount, published = published,
        isLive = liveNow || lengthSeconds == 0L,
    )
    fun SearchItemDto.toSummary() = VideoSummary(
        id = videoId, title = title, author = author, authorId = authorId, authorAvatarUrl = "",
        thumbnailUrl = bestThumbnail(videoThumbnails), lengthSeconds = lengthSeconds,
        viewCount = viewCount, published = published, isLive = liveNow || lengthSeconds == 0L,
    )
    fun VideoDetailDto.toDomain() = VideoDetail(
        summary = VideoSummary(videoId, title, author, authorId, avatar(authorThumbnails), bestThumbnail(videoThumbnails), lengthSeconds, viewCount, published, liveNow || lengthSeconds == 0L),
        description = description, likeCount = likeCount, subscriberText = subCountText,
        combinedStreams = formatStreams.map { StreamOption(UrlRewriter.rewrite(it.url), it.qualityLabel, it.type) }
            .sortedBy { it.qualityLabel.removeSuffix("p").toIntOrNull() ?: 0 },
        dashUrl = dashUrl?.let(UrlRewriter::rewrite), hlsUrl = hlsUrl?.let(UrlRewriter::rewrite),
        captions = captions.map { CaptionTrack(it.label, it.languageCode, UrlRewriter.rewrite(it.url)) },
        recommendations = recommendedVideos.map { it.toSummary() },
    )
}
