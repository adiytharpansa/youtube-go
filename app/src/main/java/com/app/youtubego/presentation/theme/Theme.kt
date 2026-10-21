package com.app.youtubego.presentation.theme
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Pilihan tema di Pengaturan. [id] disimpan di SharedPreferences, jadi JANGAN diubah urutannya. */
enum class ThemeMode(val id: Int, val label: String, val description: String) {
    SYSTEM(0, "Otomatis", "Mengikuti tema HP (terang/gelap)"),
    LIGHT(1, "Terang", "Latar putih"),
    DARK(2, "Gelap", "Latar hitam");

    /** Versi non-composable (dipakai MainActivity sebelum Compose siap). */
    fun resolveDark(systemDark: Boolean): Boolean = when (this) { SYSTEM -> systemDark; LIGHT -> false; DARK -> true }

    companion object {
        // Bawaan: ikut tema HP (SYSTEM). Ganti ke LIGHT/DARK bila ingin bawaan lain.
        val Default = SYSTEM
        fun fromId(id: Int): ThemeMode = values().firstOrNull { it.id == id } ?: Default
    }
}

/** Apakah mode ini sedang gelap? SYSTEM membaca tema sistem dan ikut berubah otomatis saat sistem berganti. */
@Composable
fun ThemeMode.isDark(): Boolean = resolveDark(isSystemInDarkTheme())

// Aksen sama di kedua tema: gradasi #FF3358 -> #FF7340 (sedikit lebih cerah agar "menyala" di atas hitam AMOLED).
val YgAccent = Color(0xFFFF3358)
val YgAccent2 = Color(0xFFFF7340)

/** Satu set warna dasar per tema. Dibaca lewat YgBackground, YgSurface, dst. (otomatis mengikuti tema aktif). */
@Immutable
class YgPalette(
    val background: Color, val surface: Color, val surfaceVariant: Color, val surfaceHigh: Color,
    val outline: Color, val onSurface: Color, val muted: Color,
)

// AMOLED: latar HITAM MURNI (#000000, piksel OLED mati total = hemat baterai + kontras tak terbatas).
// Elevasi dibuat lewat anak tangga abu sangat gelap (netral, tanpa rona biru/ungu), bukan bayangan:
// background #000000 -> surface #0B0B0E -> chip #17171B -> tinggi #222227; garis tipis #26262B.
// Teks #F5F5F7 (bukan putih murni, mengurangi silau/halo) dan redup #9D9DA8 (kontras ~7:1 di atas hitam).
val DarkPalette = YgPalette(
    background = Color(0xFF000000), surface = Color(0xFF0B0B0E), surfaceVariant = Color(0xFF17171B), surfaceHigh = Color(0xFF222227),
    outline = Color(0xFF26262B), onSurface = Color(0xFFF5F5F7), muted = Color(0xFF9D9DA8),
)
val LightPalette = YgPalette(
    background = Color(0xFFFFFFFF), surface = Color(0xFFF6F7F9), surfaceVariant = Color(0xFFEBEDF0), surfaceHigh = Color(0xFFDDE0E5),
    outline = Color(0xFFD9DCE1), onSurface = Color(0xFF0F1115), muted = Color(0xFF5F6672),
)
val LocalYgPalette = staticCompositionLocalOf { DarkPalette }

// Nama lama dipertahankan supaya seluruh layar yang sudah ada tidak perlu diubah: kini bernilai sesuai tema aktif.
val YgBackground: Color @Composable @ReadOnlyComposable get() = LocalYgPalette.current.background
val YgSurface: Color @Composable @ReadOnlyComposable get() = LocalYgPalette.current.surface
val YgSurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalYgPalette.current.surfaceVariant
val YgSurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalYgPalette.current.surfaceHigh
val YgOutline: Color @Composable @ReadOnlyComposable get() = LocalYgPalette.current.outline
val YgOnSurface: Color @Composable @ReadOnlyComposable get() = LocalYgPalette.current.onSurface
val YgMuted: Color @Composable @ReadOnlyComposable get() = LocalYgPalette.current.muted

private val DarkColors = darkColorScheme(
    primary = YgAccent, onPrimary = Color.White, secondary = YgAccent2, onSecondary = Color.White, background = DarkPalette.background, surface = DarkPalette.surface,
    surfaceVariant = DarkPalette.surfaceVariant, outline = DarkPalette.outline, onBackground = DarkPalette.onSurface,
    onSurface = DarkPalette.onSurface, onSurfaceVariant = DarkPalette.muted, error = Color(0xFFFF5B74),
    outlineVariant = Color(0xFF1C1C21),
    // Tanpa rona ungu bawaan M3 pada elevasi tonal; dialog, menu, switch, dan lembar bawah memakai anak tangga netral AMOLED.
    surfaceTint = Color.Transparent, surfaceDim = Color(0xFF000000), surfaceBright = Color(0xFF222227),
    surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF0B0B0E), surfaceContainer = Color(0xFF111115),
    surfaceContainerHigh = Color(0xFF17171B), surfaceContainerHighest = Color(0xFF222227),
    primaryContainer = Color(0xFF3A0E18), onPrimaryContainer = Color(0xFFFFD9DF),
    secondaryContainer = Color(0xFF2A1810), onSecondaryContainer = Color(0xFFFFDBCF),
    inverseSurface = Color(0xFFF5F5F7), inverseOnSurface = Color(0xFF000000), inversePrimary = Color(0xFFD01E43),
    scrim = Color(0xFF000000),
)
private val LightColors = lightColorScheme(
    primary = YgAccent, onPrimary = Color.White, secondary = YgAccent2, onSecondary = Color.White, background = LightPalette.background, surface = LightPalette.surface,
    surfaceVariant = LightPalette.surfaceVariant, outline = Color(0xFF7D8590), onBackground = LightPalette.onSurface,
    onSurface = LightPalette.onSurface, onSurfaceVariant = LightPalette.muted, error = Color(0xFFD93251),
    // Dialog, menu, dan lembar bawah memakai tingkat "surfaceContainer": diset netral agar tidak berona ungu bawaan M3.
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF6F7F9), surfaceContainer = Color(0xFFF1F2F5),
    surfaceContainerHigh = Color(0xFFEBEDF0), surfaceContainerHighest = Color(0xFFE4E6EA),
)
// Efek ripple putih transparan saat ditekan dimatikan di seluruh aplikasi.
@OptIn(ExperimentalMaterial3Api::class)
private val NoRipple = RippleConfiguration(rippleAlpha = RippleAlpha(0f, 0f, 0f, 0f))
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeGOTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val typography = remember(darkTheme) { ygTypography(palette.muted) }
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = typography, shapes = YgShapes) {
        CompositionLocalProvider(LocalRippleConfiguration provides NoRipple, LocalIndication provides PressIndication, LocalYgPalette provides palette, content = content)
    }
}

/** Jumlah layar yang sedang memaksa ikon status bar putih (mis. pemutar + Shorts bisa tumpang tindih saat transisi). */
private var forcedLightIcons = 0

/**
 * Untuk layar yang bagian atasnya selalu hitam (pemutar, Shorts): paksa ikon status bar tetap putih
 * walau tema terang aktif. Saat layar terakhir yang memaksa ditinggalkan, ikon dikembalikan sesuai TEMA
 * aktif (terang di tema gelap, gelap di tema terang), bukan nilai yang pernah ditangkap, sehingga
 * transisi yang tumpang tindih tidak lagi meninggalkan ikon pudar di layar lain.
 */
@Composable
fun ForceLightStatusBarIcons() {
    val view = LocalView.current
    val darkTheme = LocalYgPalette.current === DarkPalette
    DisposableEffect(view, darkTheme) {
        var ctx = view.context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        val window = (ctx as? Activity)?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        forcedLightIcons++
        ctrl?.isAppearanceLightStatusBars = false
        onDispose {
            forcedLightIcons = (forcedLightIcons - 1).coerceAtLeast(0)
            if (forcedLightIcons == 0) ctrl?.isAppearanceLightStatusBars = !darkTheme
        }
    }
}
