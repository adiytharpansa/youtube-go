package com.app.youtubego.presentation.theme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * SATU titik untuk mengganti font seluruh aplikasi. Untuk Inter / Plus Jakarta Sans: taruh file .ttf
 * (regular, medium, semibold, bold) di res/font, lalu ganti dengan
 * FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
 *            Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold)).
 */
val YgFontFamily: FontFamily = FontFamily.SansSerif

// Gaya teks dibangun dari bawaan Material3 lewat copy(): konstruktor langsung
// TextStyle bersifat internal pada versi Compose ini (terbukti dari CI).
private val BaseTypo = Typography()

/**
 * Hierarki ukuran: judul layar 20 / judul video 15 medium / info 12 redup / label 11-14.
 * Warna teks redup (bodySmall) mengikuti tema, jadi tipografi dibuat per tema.
 */
fun ygTypography(muted: Color) = Typography(
    titleLarge = BaseTypo.titleLarge.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleMedium = BaseTypo.titleMedium.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = BaseTypo.titleSmall.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = BaseTypo.bodyLarge.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = BaseTypo.bodyMedium.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = BaseTypo.bodySmall.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = muted),
    labelLarge = BaseTypo.labelLarge.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = BaseTypo.labelMedium.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = BaseTypo.labelSmall.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
val YgShapes = Shapes(extraSmall = RoundedCornerShape(5.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp))
