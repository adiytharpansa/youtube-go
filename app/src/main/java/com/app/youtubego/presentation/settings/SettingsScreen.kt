package com.app.youtubego.presentation.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.app.youtubego.BuildConfig
import com.app.youtubego.data.local.DownloadStore
import com.app.youtubego.data.local.UserLibraryStore
import com.app.youtubego.domain.repository.InterestRepository
import com.app.youtubego.presentation.library.ConfirmDialog
import com.app.youtubego.presentation.library.SubPageTopBar
import com.app.youtubego.presentation.library.formatSize
import com.app.youtubego.presentation.theme.ThemeMode
import com.app.youtubego.presentation.theme.YgAccent
import com.app.youtubego.presentation.theme.YgBackground
import com.app.youtubego.presentation.theme.YgMuted
import com.app.youtubego.presentation.theme.YgSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import coil.imageLoader
import com.app.youtubego.presentation.theme.YgOnSurface
import com.app.youtubego.presentation.theme.YgOutline
import com.app.youtubego.presentation.theme.YgSurfaceHigh
import com.app.youtubego.presentation.theme.YgSurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val library: UserLibraryStore,
    private val downloads: DownloadStore,
    private val interests: InterestRepository,
) : ViewModel() {
    val autoplay = library.autoplay
    val highQuality = library.highQuality
    val backgroundPlay = library.backgroundPlay
    val wifiOnlyDownloads = library.wifiOnlyDownloads
    val historyPaused = library.historyPaused
    val themeMode = library.themeMode
    val downloadItems = downloads.items

    fun setAutoplay(on: Boolean) = library.setAutoplay(on)
    fun setHighQuality(on: Boolean) = library.setHighQuality(on)
    fun setBackgroundPlay(on: Boolean) = library.setBackgroundPlay(on)
    fun setWifiOnlyDownloads(on: Boolean) = library.setWifiOnlyDownloads(on)
    fun setHistoryPaused(on: Boolean) = library.setHistoryPaused(on)
    fun setThemeMode(mode: ThemeMode) = library.setThemeMode(mode.id)
    fun clearHistory() = library.clearHistory()
    fun historySnapshot(): List<String> = library.history.value
    fun restoreHistory(ids: List<String>) = library.restoreHistory(ids)
    fun resetInterests() = interests.reset()
    fun removeAllDownloads() = downloads.removeAll()
    fun downloadsSize(): Long = downloads.totalSizeBytes()
}

private enum class Confirm { HISTORY, INTERESTS, DOWNLOADS }

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val autoplay by vm.autoplay.collectAsState()
    val highQuality by vm.highQuality.collectAsState()
    val backgroundPlay by vm.backgroundPlay.collectAsState()
    val wifiOnly by vm.wifiOnlyDownloads.collectAsState()
    val historyPaused by vm.historyPaused.collectAsState()
    val themeId by vm.themeMode.collectAsState()
    var themeDialog by remember { mutableStateOf(false) }
    var licenseDialog by remember { mutableStateOf(false) }
    val downloads by vm.downloadItems.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    // Umpan balik lewat Snackbar (bukan Toast): selaras tema, bisa punya aksi "Urungkan".
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun say(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, action, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    // Ukuran cache gambar (thumbnail) dibaca di luar thread utama.
    var cacheBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { cacheBytes = withContext(Dispatchers.IO) { context.imageLoader.diskCache?.size ?: 0L } }

    Scaffold(
        containerColor = YgBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SubPageTopBar("Pengaturan", onBack) },
        snackbarHost = {
            SnackbarHost(snackbar, modifier = Modifier.navigationBarsPadding()) { data ->
                Snackbar(data, containerColor = YgSurfaceHigh, contentColor = YgOnSurface, actionColor = YgAccent, shape = MaterialTheme.shapes.medium)
            }
        },
    ) { pad ->
        // navigationBarsPadding: baris terakhir tidak lagi tertimpa pil gestur.
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Group("Tampilan") {
                val themeMode = ThemeMode.fromId(themeId)
                ActionRow(Icons.Filled.Palette, "Tema", "${themeMode.label} \u2022 ${themeMode.description}") { themeDialog = true }
            }

            Group("Pemutaran") {
                SwitchRow(Icons.Filled.HighQuality, "Kualitas tinggi sebagai bawaan", "Mati: 720p di Wi-Fi, 480p di data seluler. Hidup: sampai 1080p. Bisa diganti di menu pemutar.", highQuality, vm::setHighQuality)
                RowDivider()
                SwitchRow(Icons.Filled.Headphones, "Putar di latar belakang", "Suara tetap jalan saat layar mati atau app dikecilkan, dengan kontrol di notifikasi.", backgroundPlay, vm::setBackgroundPlay)
                RowDivider()
                SwitchRow(Icons.Filled.SkipNext, "Putar otomatis berikutnya", "Memutar rekomendasi pertama setelah video selesai.", autoplay, vm::setAutoplay)
            }

            Group("Hemat data & unduhan") {
                SwitchRow(Icons.Filled.Wifi, "Unduh hanya lewat Wi-Fi", "Unduhan tidak memakai kuota seluler.", wifiOnly, vm::setWifiOnlyDownloads)
                RowDivider()
                ActionRow(
                    Icons.Filled.CleaningServices, "Hapus cache gambar",
                    if (cacheBytes <= 0L) "Cache kosong" else "${formatSize(cacheBytes)} dipakai thumbnail",
                    enabled = cacheBytes > 0L,
                ) {
                    scope.launch {
                        withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear() }
                        context.imageLoader.memoryCache?.clear()
                        cacheBytes = 0L
                    }
                    say("Cache gambar dibersihkan")
                }
                RowDivider()
                val used = remember(downloads) { vm.downloadsSize() }
                ActionRow(
                    Icons.Filled.DeleteSweep, "Hapus semua unduhan",
                    if (downloads.isEmpty()) "Tidak ada unduhan" else "${downloads.size} video \u2022 ${formatSize(used)}",
                    enabled = downloads.isNotEmpty(), danger = true,
                ) { confirm = Confirm.DOWNLOADS }
            }

            Group("Privasi") {
                SwitchRow(Icons.Filled.PauseCircle, "Jeda riwayat tontonan", "Video yang ditonton tidak dicatat.", historyPaused, vm::setHistoryPaused)
                RowDivider()
                ActionRow(Icons.Filled.DeleteOutline, "Hapus riwayat tontonan", "Kosongkan daftar di halaman Riwayat.", danger = true) { confirm = Confirm.HISTORY }
                RowDivider()
                ActionRow(Icons.Filled.Tune, "Atur ulang minat Beranda", "Hapus topik & channel yang dipelajari untuk \"Untuk Anda\".", danger = true) { confirm = Confirm.INTERESTS }
            }

            Group("Tentang") {
                ActionRow(Icons.Filled.Info, "YouTube GO : Next", "Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) \u2022 ketuk untuk menyalin info") {
                    clipboard.setText(AnnotatedString(
                        "YouTube GO : Next ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) \u2022 Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) \u2022 ${Build.MANUFACTURER} ${Build.MODEL}",
                    ))
                    say("Info versi disalin")
                }
                RowDivider()
                ActionRow(Icons.Filled.Description, "Lisensi sumber terbuka", "Font dan pustaka yang dipakai aplikasi ini") { licenseDialog = true }
            }
            Text(
                "Data akun, riwayat, playlist, dan unduhan hanya tersimpan di perangkat ini.",
                color = YgMuted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
            )
        }
    }

    if (themeDialog) ThemeDialog(current = ThemeMode.fromId(themeId), onSelect = { vm.setThemeMode(it); themeDialog = false }) { themeDialog = false }
    if (licenseDialog) LicenseDialog { licenseDialog = false }

    when (confirm) {
        Confirm.HISTORY -> ConfirmDialog("Hapus riwayat?", "Semua video di Riwayat akan dihapus.", "Hapus", {
            val snapshot = vm.historySnapshot()
            vm.clearHistory()
            say("Riwayat dihapus", "Urungkan") { vm.restoreHistory(snapshot) }
        }, destructive = true) { confirm = null }
        Confirm.INTERESTS -> ConfirmDialog("Atur ulang minat?", "Beranda \"Untuk Anda\" akan kembali ke video populer sampai kamu menonton atau mencari lagi.", "Atur ulang", {
            vm.resetInterests(); say("Minat diatur ulang")
        }, destructive = true) { confirm = null }
        Confirm.DOWNLOADS -> ConfirmDialog("Hapus semua unduhan?", "Video yang sudah diunduh akan dihapus dari perangkat.", "Hapus", {
            vm.removeAllDownloads(); say("Unduhan dihapus")
        }, destructive = true) { confirm = null }
        null -> Unit
    }
}

/** Satu bagian: judul kecil beraksen + kartu bulat berisi baris-barisnya. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title, color = YgAccent, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 32.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(shape).background(YgSurface).border(1.dp, YgOutline, shape), content = content)
}

/** Garis pemisah antarbaris, dimulai sejajar teks (melewati ikon). */
@Composable
private fun RowDivider() = HorizontalDivider(Modifier.padding(start = 68.dp), thickness = 0.5.dp, color = YgOutline)

@Composable
private fun LeadingIcon(icon: ImageVector, tint: Color) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(YgSurfaceVariant), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(it) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon, YgAccent)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, color = YgMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = YgAccent, checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = YgMuted, uncheckedTrackColor = YgSurfaceHigh, uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val titleColor = when { !enabled -> YgMuted; danger -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface }
    val iconColor = when { !enabled -> YgMuted; danger -> MaterialTheme.colorScheme.error; else -> YgAccent }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon, iconColor)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), color = titleColor)
            Text(subtitle, color = YgMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Daftar ringkas lisensi pihak ketiga (teks lengkap ada di repositori masing-masing). */
@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = YgSurface,
        title = { Text("Lisensi sumber terbuka") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "Poppins (font)" to "SIL Open Font License 1.1",
                    "NewPipeExtractor" to "GNU GPL v3",
                    "Jetpack Compose, Navigation, Media3, Hilt" to "Apache License 2.0",
                    "Coil, OkHttp" to "Apache License 2.0",
                    "Kotlinx Coroutines & Serialization" to "Apache License 2.0",
                ).forEach { (name, license) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(license, color = YgMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Teks lengkap tiap lisensi tersedia di repositori masing-masing.", color = YgMuted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

/** Pilih tema: langsung berlaku begitu salah satu dipilih. */
@Composable
private fun ThemeDialog(current: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = YgSurface,
        title = { Text("Pilih tema") },
        text = {
            Column {
                ThemeMode.values().forEach { mode ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == current, onClick = { onSelect(mode) }, colors = RadioButtonDefaults.colors(selectedColor = YgAccent))
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(mode.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
                            Text(mode.description, color = YgMuted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
