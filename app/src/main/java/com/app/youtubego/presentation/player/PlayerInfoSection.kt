package com.app.youtubego.presentation.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.app.youtubego.R
import com.app.youtubego.data.local.DownloadStatus
import com.app.youtubego.domain.model.CommentItem
import com.app.youtubego.domain.model.VideoDetail
import com.app.youtubego.presentation.common.UiState
import com.app.youtubego.presentation.common.VideoCardShimmer
import com.app.youtubego.presentation.common.formatRelativeTime
import com.app.youtubego.presentation.common.formatViewsId
import com.app.youtubego.presentation.common.shimmer
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurfaceHigh
import com.app.youtubego.presentation.theme.YgSurfaceVariant
import com.app.youtubego.presentation.theme.YouTubeGOTheme

/*
 * Bagian info di bawah pemutar (dipisah dari PlayerScreen.kt): judul, channel, aksi, komentar, header
 * "Video berikutnya", dan skeleton saat memuat. Semua teks lewat strings.xml.
 */

@Composable
internal fun TitleBlock(d: VideoDetail, onMore: () -> Unit) {
    val meta = listOfNotNull(
        stringResource(R.string.player_views, formatViewsId(d.summary.viewCount)),
        formatRelativeTime(d.summary.published).takeIf { it.isNotEmpty() },
    ).joinToString(" • ")
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.player_open_description), role = Role.Button, onClick = onMore)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(d.summary.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("$meta  ${stringResource(R.string.player_more)}", color = YgMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun ChannelRow(d: VideoDetail, subscribed: Boolean, onOpen: () -> Unit, onSubscribe: () -> Unit) {
    val canOpen = d.summary.authorId.isNotBlank()
    val openLabel = stringResource(R.string.channel_open, d.summary.author)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            d.summary.authorAvatarUrl, null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(YgSurfaceVariant)
                .clickable(enabled = canOpen, onClickLabel = openLabel, role = Role.Button, onClick = onOpen),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp).clickable(enabled = canOpen, onClickLabel = openLabel, role = Role.Button, onClick = onOpen)) {
            Text(d.summary.author, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.subscriberText.isNotBlank()) Text(d.subscriberText, color = YgMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        if (canOpen) {
            if (subscribed) FilledTonalButton(onClick = onSubscribe) {
                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.player_subscribed))
            } else Button(
                onClick = onSubscribe,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background),
            ) { Text(stringResource(R.string.player_subscribe)) }
        }
    }
}

@Composable
internal fun ActionRow(
    downloadStatus: DownloadStatus?, onDownload: () -> Unit, onPlaylist: () -> Unit,
    likeLabel: String, liked: Boolean, saved: Boolean, onLike: () -> Unit, onSave: () -> Unit, onShare: () -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "like") { ActionPill(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, likeLabel, liked, onLike) }
        item(key = "share") { ActionPill(Icons.Filled.Share, stringResource(R.string.player_share), false, onShare) }
        item(key = "save") {
            ActionPill(
                if (saved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                stringResource(if (saved) R.string.player_saved else R.string.player_save), saved, onSave,
            )
        }
        item(key = "playlist") { ActionPill(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.player_playlist), false, onPlaylist) }
        item(key = "download") {
            val label = stringResource(
                when (downloadStatus) {
                    DownloadStatus.DONE -> R.string.player_downloaded
                    DownloadStatus.DOWNLOADING -> R.string.player_downloading
                    DownloadStatus.PAUSED, DownloadStatus.FAILED -> R.string.player_resume_download
                    null -> R.string.player_download
                },
            )
            ActionPill(if (downloadStatus == DownloadStatus.DONE) Icons.Filled.DownloadDone else Icons.Filled.Download, label, downloadStatus == DownloadStatus.DONE) {
                if (downloadStatus != DownloadStatus.DONE && downloadStatus != DownloadStatus.DOWNLOADING) onDownload()
            }
        }
    }
}

/** Pill aksi: tampilan ±36dp, tetapi area sentuh minimal 48dp (minimumInteractiveComponentSize). Warna ikon beranimasi. */
@Composable
internal fun ActionPill(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (active) YtRed else MaterialTheme.colorScheme.onSurface, label = "pillTint")
    Box(Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(CircleShape).background(YgSurfaceVariant)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Ikon dekoratif: label teks di sampingnya sudah dibaca TalkBack.
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(" $label", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun CommentsTeaser(comments: UiState<List<CommentItem>>, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(MaterialTheme.shapes.medium)
            .background(YgSurfaceVariant)
            .clickable(onClickLabel = stringResource(R.string.comments_open), role = Role.Button, onClick = onOpen)
            .padding(12.dp),
    ) {
        Text(stringResource(R.string.comments_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        when (comments) {
            is UiState.Loading -> MutedLine(stringResource(R.string.comments_loading))
            is UiState.Error -> MutedLine(stringResource(R.string.comments_unavailable))
            is UiState.Success -> {
                val top = comments.data.firstOrNull()
                if (top == null) MutedLine(stringResource(R.string.comments_empty))
                else Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(top.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(24.dp).clip(CircleShape).background(YgSurfaceHigh))
                    Text(cleanHtml(top.content), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun MutedLine(text: String) =
    Text(text, color = YgMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))

/** "Video berikutnya" + sakelar Putar otomatis. Seluruh baris bisa disentuh dan dibaca TalkBack sebagai satu sakelar. */
@Composable
internal fun NextVideosHeader(autoplay: Boolean, onAutoplayChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.player_next_videos), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Row(
            Modifier.clip(CircleShape).toggleable(value = autoplay, role = Role.Switch, onValueChange = onAutoplayChange).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.player_autoplay), color = YgMuted, style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = autoplay, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp),
                colors = SwitchDefaults.colors(
                    checkedTrackColor = YtRed, checkedThumbColor = Color.White, checkedBorderColor = YtRed,
                    uncheckedTrackColor = YgSurfaceHigh, uncheckedThumbColor = YgMuted, uncheckedBorderColor = YgMuted,
                ),
            )
        }
    }
}

/** Skeleton sesuai tata letak asli (judul, channel, pill, kartu) supaya tidak ada lompatan layout saat data tiba. */
@Composable
internal fun PlayerInfoShimmer() {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Spacer(Modifier.fillMaxWidth(0.9f).height(16.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
        }
        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(40.dp).clip(CircleShape).shimmer())
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Spacer(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
                Spacer(Modifier.height(6.dp))
                Spacer(Modifier.fillMaxWidth(0.3f).height(11.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
            }
            Spacer(Modifier.width(112.dp).height(40.dp).clip(CircleShape).shimmer())
        }
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { Spacer(Modifier.width(92.dp).height(36.dp).clip(CircleShape).shimmer()) }
        }
        VideoCardShimmer()
        VideoCardShimmer()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D)
@Composable
private fun ActionRowPreview() = YouTubeGOTheme(darkTheme = true) {
    ActionRow(downloadStatus = null, onDownload = {}, onPlaylist = {}, likeLabel = "17,9 rb", liked = true, saved = false, onLike = {}, onSave = {}, onShare = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D)
@Composable
private fun CommentsTeaserPreview() = YouTubeGOTheme(darkTheme = true) {
    CommentsTeaser(UiState.Success(listOf(CommentItem("Budi", "", "kesedihan yg bisa dirasakan hanya pada manusia yg mempunyai hati", 0L, 3L))), onOpen = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D)
@Composable
private fun NextVideosHeaderPreview() = YouTubeGOTheme(darkTheme = true) { NextVideosHeader(autoplay = true, onAutoplayChange = {}) }

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D, heightDp = 600)
@Composable
private fun PlayerInfoShimmerPreview() = YouTubeGOTheme(darkTheme = true) { PlayerInfoShimmer() }
