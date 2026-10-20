package com.app.youtubego.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.youtubego.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val autoplay by vm.autoplay.collectAsState()
    Scaffold(
        containerColor = YgBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = YgBackground)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(YgBackground)) {
            Text("PEMUTARAN", color = YgMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(YgSurfaceElevated, MaterialTheme.shapes.medium).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayCircle, null, tint = YgAccent, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Putar otomatis", style = MaterialTheme.typography.titleSmall)
                    Text("Putar video rekomendasi berikutnya", color = YgMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoplay, onCheckedChange = vm::setAutoplay)
            }
            Spacer(Modifier.height(22.dp))
            Text("TENTANG GO", color = YgMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
            ListItem(
                headlineContent = { Text("Mode hemat data") },
                supportingContent = { Text("Pemutar memulai dari kualitas rendah; pilihan kualitas tersedia di pengaturan video.", color = YgMuted) },
                leadingContent = { Icon(Icons.Default.Tune, null, tint = YgAccent) },
                colors = ListItemDefaults.colors(containerColor = YgSurfaceElevated)
            )
            ListItem(
                headlineContent = { Text("Data akun") },
                supportingContent = { Text("Koleksi dan langganan disimpan lokal di perangkat ini.", color = YgMuted) },
                leadingContent = { Icon(Icons.Default.Security, null, tint = YgAccent) },
                colors = ListItemDefaults.colors(containerColor = YgSurfaceElevated)
            )
        }
    }
}
