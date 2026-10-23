package com.app.playtubelite.presentation.home
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.playtubelite.domain.model.InterestProfile
import com.app.playtubelite.domain.model.TopicCatalog
import com.app.playtubelite.presentation.theme.PtMuted

/** Panel "Minat kamu": pilih topik, lihat & hapus minat yang dipelajari dari tontonan/pencarian. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InterestSheet(
    profile: InterestProfile,
    onToggleSeed: (String) -> Unit,
    onForgetTopic: (String) -> Unit,
    onForgetChannel: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Pilih minat kamu", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("Beranda \"Untuk Anda\" akan menampilkan video sesuai topik ini, dan terus menyesuaikan dari apa yang kamu cari & tonton. Data hanya disimpan di perangkat ini.", color = PtMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TopicCatalog.all.forEach { t ->
                    FilterChip(selected = t.id in profile.seeds, onClick = { onToggleSeed(t.id) }, label = { Text(t.label) })
                }
            }
            val learnedTopics = profile.topics.entries.sortedByDescending { it.value }.take(12)
            val learnedChannels = profile.channels.entries.sortedByDescending { it.value.score }.take(6)
            if (learnedTopics.isNotEmpty() || learnedChannels.isNotEmpty()) {
                Text("Dipelajari dari aktivitasmu", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    learnedChannels.forEach { (id, c) ->
                        InputChip(selected = true, onClick = { onForgetChannel(id) }, label = { Text(c.name) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Hapus ${c.name}", modifier = Modifier.size(16.dp)) })
                    }
                    learnedTopics.forEach { (k, _) ->
                        InputChip(selected = false, onClick = { onForgetTopic(k) }, label = { Text(k) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Hapus $k", modifier = Modifier.size(16.dp)) })
                    }
                }
                TextButton(onClick = onReset, modifier = Modifier.padding(top = 4.dp)) { Text("Reset semua minat") }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Selesai") }
        }
    }
}
