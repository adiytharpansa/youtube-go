package com.app.youtubego
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.app.youtubego.presentation.navigation.YoutubeGoNavHost
import com.app.youtubego.presentation.theme.YouTubeGOTheme
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Android 13+: tanpa izin ini notifikasi kontrol media (putar/jeda di latar belakang) tidak tampil.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            // Dark Theme adalah DEFAULT (bukan mengikuti sistem) - sesuai konsep web app.
            YouTubeGOTheme(darkTheme = true) { YoutubeGoNavHost() }
        }
    }
}
