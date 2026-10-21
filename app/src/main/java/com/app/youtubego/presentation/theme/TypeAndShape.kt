package com.app.youtubego.presentation.theme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.youtubego.R

/**
 * SATU titik untuk mengganti font seluruh aplikasi. Untuk Inter / Plus Jakarta Sans: taruh file .ttf
 * (regular, medium, semibold, bold) di res/font, lalu ganti dengan
 * FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),
 *            Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold)).
 */
val YgFontFamily: FontFamily = FontFamily.SansSerif

/**
 * Font tampilan (Poppins, lisensi OFL) untuk judul layar, tombol, chip, dan label navigasi: memberi identitas
 * tanpa membuat judul video (teks padat) jadi lebih lebar. Berkas ada di res/font (regular, medium, bold).
 * Ingin Inter di seluruh aplikasi? Ganti isi YgDisplayFamily dengan file Inter dan set YgFontFamily = YgDisplayFamily.
 */
val YgDisplayFamily: FontFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

// Gaya teks dibangun dari bawaan Material3 lewat copy(): konstruktor langsung
// TextStyle bersifat internal pada versi Compose ini (terbukti dari CI).
private val BaseTypo = Typography()

/**
 * Hierarki ukuran: judul layar 20 / judul video 15 medium / info 12 redup / label 11-14.
 * Warna teks redup (bodySmall) mengikuti tema, jadi tipografi dibuat per tema.
 */
fun ygTypography(muted: Color) = Typography(
    titleLarge = BaseTypo.titleLarge.copy(fontFamily = YgDisplayFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleMedium = BaseTypo.titleMedium.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = BaseTypo.titleSmall.copy(fontFamily = YgDisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = BaseTypo.bodyLarge.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = BaseTypo.bodyMedium.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = BaseTypo.bodySmall.copy(fontFamily = YgFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp, color = muted),
    labelLarge = BaseTypo.labelLarge.copy(fontFamily = YgDisplayFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = BaseTypo.labelMedium.copy(fontFamily = YgDisplayFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
    labelSmall = BaseTypo.labelSmall.copy(fontFamily = YgDisplayFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.1.sp),
)
val YgShapes = Shapes(extraSmall = RoundedCornerShape(5.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp))
