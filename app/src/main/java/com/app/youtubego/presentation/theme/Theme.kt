package com.app.youtubego.presentation.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
// Token warna PERSIS dari web app (CSS :root dark): bg #0A0B0D, surface #101114, chip #1C1E23,
// garis #23252B, teks #F3F4F6, redup #9AA0AB, aksen gradasi #FF2E4D -> #FF6A3D.
val YgBackground = Color(0xFF0A0B0D)
val YgSurface = Color(0xFF101114)
val YgSurfaceVariant = Color(0xFF1C1E23)
val YgOutline = Color(0xFF23252B)
val YgOnSurface = Color(0xFFF3F4F6)
val YgMuted = Color(0xFF9AA0AB)
val YgAccent = Color(0xFFFF2E4D)
val YgAccent2 = Color(0xFFFF6A3D)
private val DarkColors = darkColorScheme(
    primary = YgAccent, secondary = YgAccent2, background = YgBackground, surface = YgSurface,
    surfaceVariant = YgSurfaceVariant, outline = YgOutline, onBackground = YgOnSurface,
    onSurface = YgOnSurface, onSurfaceVariant = YgMuted, error = Color(0xFFFF5B74),
)
private val LightColors = lightColorScheme(primary = YgAccent, secondary = YgAccent2)
// Efek ripple putih transparan saat ditekan dimatikan di seluruh aplikasi.
private val NoRipple = RippleConfiguration(rippleAlpha = RippleAlpha(0f, 0f, 0f, 0f))
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeGOTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = YgTypography, shapes = YgShapes) {
        CompositionLocalProvider(LocalRippleConfiguration provides NoRipple, content = content)
    }
}
