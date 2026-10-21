package com.app.youtubego.presentation.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val downloads by vm.downloadItems.collectAsState()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    Scaffold(containerColor = YgBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = { SubPageTopBar("Pengaturan", onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Section("Tampilan")
            val themeMode = ThemeMode.fromId(themeId)
            ActionRow("Tema", "${themeMode.label} \u2022 ${themeMode.description}") { themeDialog = true }

            Section("Pemutaran")
            SwitchRow("Kualitas tinggi sebagai bawaan", "Mati = mulai dari kualitas terendah agar hemat data (360p). Bisa diganti per video di menu pemutar.", highQuality, vm::setHighQuality)
            SwitchRow("Putar di latar belakang", "Suara tetap berjalan saat layar mati atau aplikasi ditutup ke Beranda, dengan kontrol di notifikasi.", backgroundPlay, vm::setBackgroundPlay)
            SwitchRow("Putar otomatis video berikutnya", "Memutar rekomendasi pertama setelah video selesai.", autoplay, vm::setAutoplay)

            Section("Hemat data & unduhan")
            SwitchRow("Unduh hanya lewat Wi-Fi", "Mencegah unduhan memakai kuota seluler.", wifiOnly, vm::setWifiOnlyDownloads)
            val used = remember(downloads) { vm.downloadsSize() }
            ActionRow("Hapus semua unduhan", if (downloads.isEmpty()) "Tidak ada unduhan" else "${downloads.size} video • ${formatSize(used)}", enabled = downloads.isNotEmpty()) { confirm = Confirm.DOWNLOADS }

            Section("Privasi")
            SwitchRow("Jeda riwayat tontonan", "Video yang ditonton tidak dicatat ke Riwayat.", historyPaused, vm::setHistoryPaused)
            ActionRow("Hapus riwayat tontonan", "Mengosongkan daftar di halaman Riwayat.") { confirm = Confirm.HISTORY }
            ActionRow("Atur ulang minat Beranda", "Menghapus topik & channel yang dipelajari untuk rekomendasi \"Untuk Anda\".") { confirm = Confirm.INTERESTS }

            Section("Tentang")
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("YouTube GO : Next", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text("Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = YgMuted, style = MaterialTheme.typography.bodySmall)
                Text("Data akun, riwayat, playlist, dan unduhan hanya tersimpan di perangkat ini.", color = YgMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    if (themeDialog) ThemeDialog(current = ThemeMode.fromId(themeId), onSelect = { vm.setThemeMode(it); themeDialog = false }) { themeDialog = false }

    when (confirm) {
        Confirm.HISTORY -> ConfirmDialog("Hapus riwayat?", "Semua video di Riwayat akan dihapus.", "Hapus", { vm.clearHistory(); toast("Riwayat dihapus") }) { confirm = null }
        Confirm.INTERESTS -> ConfirmDialog("Atur ulang minat?", "Beranda \"Untuk Anda\" akan kembali ke video populer sampai kamu menonton atau mencari lagi.", "Atur ulang", { vm.resetInterests(); toast("Minat diatur ulang") }) { confirm = null }
        Confirm.DOWNLOADS -> ConfirmDialog("Hapus semua unduhan?", "Video yang sudah diunduh akan dihapus dari perangkat.", "Hapus", { vm.removeAllDownloads(); toast("Unduhan dihapus") }) { confirm = null }
        null -> Unit
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = YgAccent, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, color = YgMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = YgAccent))
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else YgMuted)
        Text(subtitle, color = YgMuted, style = MaterialTheme.typography.bodySmall)
    }
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
