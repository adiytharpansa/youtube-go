package com.app.playtubelite.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.app.playtubelite.domain.model.Chapter
import com.app.playtubelite.presentation.common.formatDuration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.app.playtubelite.domain.model.CommentItem
import com.app.playtubelite.domain.model.VideoDetail
import com.app.playtubelite.presentation.common.UiState
import com.app.playtubelite.presentation.common.formatRelativeTime
import com.app.playtubelite.presentation.common.formatViewsId
import com.app.playtubelite.presentation.theme.PtMuted
import com.app.playtubelite.presentation.theme.PtSurface
import com.app.playtubelite.presentation.theme.PtSurfaceVariant

/** Bersihkan sisa HTML dari API (mis. &nbsp;, <br>, <a>) agar komentar/deskripsi tampil sebagai teks biasa. */
internal fun cleanHtml(s: String): String {
    if (s.indexOf('<') < 0 && s.indexOf('&') < 0) return s
    fun decode(x: String) = androidx.core.text.HtmlCompat.fromHtml(x, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
    var out = decode(s)
    // Sumber komentar kadang ter-escape dua kali ("&amp;nbsp;"): sisa entitas tampil literal, jadi urai sekali lagi.
    if (out.indexOf('&') >= 0) out = decode(out)
    return out.replace('\u00A0', ' ').trim()
}

internal data class Choice(val label: String, val selected: Boolean, val onPick: () -> Unit)
private enum class SettingsPage { ROOT, QUALITY, SPEED, CAPTIONS, SLEEP, CAPTION_SIZE, CAPTION_STYLE }

/** Panel pengaturan pemutar: Kualitas, Kecepatan pemutaran, Teks (CC). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSettingsSheet(
    qualityLabel: String, qualityChoices: List<Choice>,
    speedLabel: String, speedChoices: List<Choice>,
    captionLabel: String, captionChoices: List<Choice>,
    loopOn: Boolean, onLoopChange: (Boolean) -> Unit,
    zoomFill: Boolean, onZoomChange: (Boolean) -> Unit,
    backgroundPlay: Boolean, onBackgroundPlayChange: (Boolean) -> Unit,
    sleepLabel: String, sleepChoices: List<Choice>,
    captionSizeLabel: String = "", captionSizeChoices: List<Choice> = emptyList(),
    captionStyleLabel: String = "", captionStyleChoices: List<Choice> = emptyList(),
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PtSurface) {
        when (page) {
            SettingsPage.ROOT -> {
                if (qualityChoices.isNotEmpty()) SettingRow(Icons.Default.Tune, "Kualitas", qualityLabel) { page = SettingsPage.QUALITY }
                SettingRow(Icons.Default.Speed, "Kecepatan pemutaran", speedLabel) { page = SettingsPage.SPEED }
                if (captionChoices.isNotEmpty()) SettingRow(Icons.Default.ClosedCaption, "Teks (CC)", captionLabel) { page = SettingsPage.CAPTIONS }
                if (captionChoices.isNotEmpty() && captionSizeChoices.isNotEmpty()) {
                    SettingRow(Icons.Default.FormatSize, "Ukuran teks CC", captionSizeLabel) { page = SettingsPage.CAPTION_SIZE }
                    SettingRow(Icons.Default.Palette, "Gaya teks CC", captionStyleLabel) { page = SettingsPage.CAPTION_STYLE }
                }
                SettingRow(Icons.Default.Bedtime, "Timer tidur", sleepLabel) { page = SettingsPage.SLEEP }
                SettingSwitchRow(Icons.Default.Repeat, "Ulangi video", loopOn, onLoopChange)
                SettingSwitchRow(Icons.Default.AspectRatio, "Isi layar (potong tepi)", zoomFill, onZoomChange)
                SettingSwitchRow(Icons.Default.Headphones, "Putar di latar belakang", backgroundPlay, onBackgroundPlayChange)
            }
            else -> {
                val (title, choices) = when (page) {
                    SettingsPage.QUALITY -> "Kualitas" to qualityChoices
                    SettingsPage.SPEED -> "Kecepatan pemutaran" to speedChoices
                    SettingsPage.SLEEP -> "Timer tidur" to sleepChoices
                    SettingsPage.CAPTION_SIZE -> "Ukuran teks CC" to captionSizeChoices
                    SettingsPage.CAPTION_STYLE -> "Gaya teks CC" to captionStyleChoices
                    else -> "Teks (CC)" to captionChoices
                }
                ListItem(
                    headlineContent = { Text(title, fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.Default.KeyboardArrowLeft, "Kembali") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { page = SettingsPage.ROOT },
                )
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { c ->
                        ListItem(
                            headlineContent = { Text(c.label) },
                            leadingContent = { if (c.selected) Icon(Icons.Default.Check, null, tint = YtRed) else Spacer(Modifier.size(24.dp)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { c.onPick(); onDismiss() },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingSwitchRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = YtRed)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Text(value, color = PtMuted) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Panel komentar: urut Teratas (urutan YouTube) / Terbaru (dari komentar yang sudah dimuat); penanda waktu bisa diketuk. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(comments: UiState<List<CommentItem>>, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    var sort by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PtSurface) {
        Text("Komentar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sort == 0, onClick = { sort = 0 }, label = { Text("Teratas") })
            FilterChip(selected = sort == 1, onClick = { sort = 1 }, label = { Text("Terbaru") })
        }
        when (comments) {
            is UiState.Loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> Text(comments.message, color = PtMuted, modifier = Modifier.padding(16.dp))
            is UiState.Success -> {
                val list = remember(comments.data, sort) { if (sort == 1) comments.data.sortedByDescending { it.published } else comments.data }
                if (list.isEmpty()) {
                    Text("Belum ada komentar atau komentar dinonaktifkan.", color = PtMuted, modifier = Modifier.padding(16.dp))
                } else LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(list) { c ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            AsyncImage(c.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(34.dp).clip(CircleShape).background(PtSurfaceVariant))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(listOf(c.author, formatRelativeTime(c.published)).filter { it.isNotBlank() }.joinToString(" • "), color = PtMuted, style = MaterialTheme.typography.labelMedium)
                                TimestampText(cleanHtml(c.content), Modifier.padding(top = 2.dp)) { ms -> onSeek(ms); onDismiss() }
                                if (c.likeCount > 0) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ThumbUp, null, tint = PtMuted, modifier = Modifier.size(14.dp))
                                    Text(" ${formatViewsId(c.likeCount)}", color = PtMuted, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Panel deskripsi lengkap ("...selengkapnya"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DescriptionSheet(d: VideoDetail, onSeek: (Long) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PtSurface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(d.summary.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat(if (d.likeCount > 0) formatViewsId(d.likeCount) else "-", "suka")
                Stat(formatViewsId(d.summary.viewCount), "tontonan")
                formatRelativeTime(d.summary.published).takeIf { it.isNotEmpty() }?.let { Stat(it, "diunggah") }
            }
            val text = remember(d.description) { cleanHtml(d.description) }
            if (text.isBlank()) Text("Tidak ada deskripsi.", style = MaterialTheme.typography.bodyMedium)
            else TimestampText(text) { ms -> onSeek(ms); onDismiss() }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, color = PtMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private val TIMESTAMP = Regex("""(?<![\d:])(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?![\d:])""")

/** Teks deskripsi dengan penanda waktu (mis. 1:23) yang bisa diketuk untuk melompat, seperti di YouTube. */
@Composable
private fun TimestampText(text: String, modifier: Modifier = Modifier, onSeek: (Long) -> Unit) {
    val annotated = remember(text) {
        buildAnnotatedString {
            var last = 0
            for (m in TIMESTAMP.findAll(text)) {
                val h = m.groupValues[1].toLongOrNull() ?: 0L
                val mi = m.groupValues[2].toLong()
                val sec = m.groupValues[3].toLong()
                if (sec >= 60 || (m.groupValues[1].isNotEmpty() && mi >= 60)) continue
                append(text.substring(last, m.range.first))
                pushStringAnnotation("seek", ((h * 3600 + mi * 60 + sec) * 1000).toString())
                withStyle(SpanStyle(color = Color(0xFF3EA6FF))) { append(m.value) }
                pop()
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = LocalContentColor.current),
        onClick = { off -> annotated.getStringAnnotations("seek", off, off).firstOrNull()?.let { onSeek(it.item.toLong()) } },
    )
}

/** Daftar bab video (ketuk judul bab di kontrol pemutar): ketuk satu bab untuk melompat ke sana. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersSheet(chapters: List<Chapter>, currentMs: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val current = chapters.indexOfLast { it.startMs <= currentMs }.coerceAtLeast(0)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PtSurface) {
        Text("Bab", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(chapters) { i, c ->
                ListItem(
                    headlineContent = { Text(c.title.ifBlank { "Bab ${i + 1}" }, fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal, maxLines = 2) },
                    supportingContent = { Text(formatDuration(c.startMs / 1000), color = if (i == current) YtRed else PtMuted) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(c.startMs); onDismiss() },
                )
            }
        }
    }
}
