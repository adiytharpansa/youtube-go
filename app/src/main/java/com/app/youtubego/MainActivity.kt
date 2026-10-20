package com.app.youtubego
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.app.youtubego.presentation.navigation.YoutubeGoNavHost
import com.app.youtubego.presentation.theme.YouTubeGOTheme
import dagger.hilt.android.AndroidEntryPoint
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Dark Theme adalah DEFAULT (bukan mengikuti sistem) - sesuai konsep web app.
            YouTubeGOTheme(darkTheme = true) { YoutubeGoNavHost() }
        }
    }
}
