package com.app.youtubego

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.app.youtubego.presentation.navigation.YoutubeGoNavHost
import com.app.youtubego.presentation.theme.YouTubeGOTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Dark theme dipaksa oleh GO: ikon status dan navigasi harus selalu terang di atas latar gelap.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
        setContent {
            YouTubeGOTheme(darkTheme = true) { YoutubeGoNavHost() }
        }
    }
}
