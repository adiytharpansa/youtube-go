package com.app.youtubego.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tipografi premium — hierarki tegas, tracking rapat ala YouTube / Spotify
// Siap ganti font ke Inter/Poppins via res/font cukup ubah FontFamily di satu tempat.
private val BaseTypo = Typography()

val YgTypography = Typography(
    // Judul besar — untuk hero / empty state
    displaySmall = BaseTypo.displaySmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.6).sp
    ),
    // Judul halaman
    headlineSmall = BaseTypo.headlineSmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp
    ),
    // Judul kartu video — paling penting
    titleLarge = BaseTypo.titleLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.35).sp
    ),
    titleMedium = BaseTypo.titleMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.15).sp
    ),
    titleSmall = BaseTypo.titleSmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyLarge = BaseTypo.bodyLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = BaseTypo.bodyMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = BaseTypo.bodySmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        color = YgMuted
    ),
    labelLarge = BaseTypo.labelLarge.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = BaseTypo.labelMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.15.sp
    ),
    labelSmall = BaseTypo.labelSmall.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        letterSpacing = 0.3.sp
    ),
)

// Shapes — sudut membulat khas GO: kartu 16dp, chip 12dp, pill 100dp
val YgShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
