package com.app.playtubelite
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.app.playtubelite.data.local.UserLibraryStore
import com.app.playtubelite.presentation.navigation.PlaytubeNavHost
import com.app.playtubelite.presentation.theme.DarkPalette
import com.app.playtubelite.presentation.theme.LightPalette
import com.app.playtubelite.presentation.theme.ThemeMode
import com.app.playtubelite.presentation.theme.PlaytubeLiteTheme
import com.app.playtubelite.presentation.theme.isDark
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var library: UserLibraryStore

    // Android 13+: tanpa izin ini notifikasi kontrol media (putar/jeda di latar belakang) tidak tampil.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Status bar & bar navigasi mengikuti TEMA APLIKASI (bukan tema sistem): ikon gelap di tema terang, terang di tema gelap. */
    private fun applyTheme(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(Color.argb(0xe6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x00, 0x00, 0x00)) { dark },
        )
        // Warna jendela di balik Compose (terlihat sesaat saat keyboard/rotasi) ikut tema.
        window.setBackgroundDrawable(ColorDrawable((if (dark) DarkPalette.background else LightPalette.background).toArgb()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        applyTheme(ThemeMode.fromId(library.themeMode.value).resolveDark(systemNight))
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            // Pilihan di Pengaturan > Tampilan: Otomatis (ikut sistem) / Terang / Gelap. Bawaan: Terang.
            val modeId by library.themeMode.collectAsState()
            val dark = ThemeMode.fromId(modeId).isDark()
            DisposableEffect(dark) { applyTheme(dark); onDispose { } }
            PlaytubeLiteTheme(darkTheme = dark) { PlaytubeNavHost() }
        }
    }
}
