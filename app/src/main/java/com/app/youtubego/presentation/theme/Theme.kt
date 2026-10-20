package com.app.youtubego.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ────────────────────────────────────────────────────────────
// Token warna — 100% dari identitas YouTube GO web
// Diperkaya untuk tampilan premium modern
// ────────────────────────────────────────────────────────────
val YgBackground = Color(0xFF0A0B0D)
val YgBackgroundSoft = Color(0xFF0F1014)
val YgSurface = Color(0xFF101114)
val YgSurfaceElevated = Color(0xFF16181E)
val YgSurfaceVariant = Color(0xFF1C1E23)
val YgSurfaceHigh = Color(0xFF23252B)
val YgSurfaceHigher = Color(0xFF2A2D35)
val YgOutline = Color(0xFF23252B)
val YgOutlineStrong = Color(0xFF2E323D)
val YgDivider = Color(0xFF1E2028)
val YgOnSurface = Color(0xFFF3F4F6)
val YgOnSurfaceStrong = Color(0xFFFFFFFF)
val YgMuted = Color(0xFF9AA0AB)
val YgMutedSoft = Color(0xFF767C89)
val YgAccent = Color(0xFFFF2E4D)
val YgAccent2 = Color(0xFFFF6A3D)
val YgAccentSoft = Color(0xFFFF2E4D)
val YgAccentGlow = Color(0x33FF2E4D)
val YgLive = Color(0xFFFF3040)
val YgSuccess = Color(0xFF22C55E)

// Gradient aksen utama — dipakai untuk tombol, chip terpilih, avatar ring, dll
val YgGradientAccent = Brush.horizontalGradient(listOf(YgAccent, YgAccent2))
val YgGradientAccentVertical = Brush.verticalGradient(listOf(YgAccent, YgAccent2))
val YgGradientBgTop = Brush.verticalGradient(
    listOf(Color(0xFF15161B), YgBackground)
)
val YgGradientCardSheen = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.08f), Color.Transparent, Color.Transparent)
)
val YgScrim = Color(0x990A0B0D)
val YgScrimStrong = Color(0xCC0A0B0D)

// Dark colorScheme — Material 3 lengkap (surfaceContainer untuk sheet/nav)
private val DarkColors = darkColorScheme(
    primary = YgAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A0E18),
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = YgAccent2,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4A2310),
    tertiary = Color(0xFFE8B4B8),
    background = YgBackground,
    onBackground = YgOnSurface,
    surface = YgSurface,
    onSurface = YgOnSurface,
    surfaceVariant = YgSurfaceVariant,
    onSurfaceVariant = YgMuted,
    surfaceContainer = YgSurfaceElevated,
    surfaceContainerHigh = YgSurfaceHigh,
    surfaceContainerHighest = YgSurfaceHigher,
    outline = YgOutline,
    outlineVariant = YgDivider,
    error = Color(0xFFFF5B74),
    scrim = YgScrimStrong,
)

private val LightColors = lightColorScheme(
    primary = YgAccent,
    secondary = YgAccent2,
    background = Color(0xFFF8F9FB),
    surface = Color.White,
)

data class YgGradients(
    val accent: Brush = YgGradientAccent,
    val accentGlow: Color = YgAccentGlow,
)

val LocalYgGradients = staticCompositionLocalOf { YgGradients() }

@Composable
fun YouTubeGOTheme(
    darkTheme: Boolean = true, // GO selalu dark sebagai default — sesuai identitas
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalYgGradients provides YgGradients()) {
        MaterialTheme(
            colorScheme = scheme,
            typography = YgTypography,
            shapes = YgShapes,
            content = content
        )
    }
}
