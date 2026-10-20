package com.app.youtubego.presentation.theme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// Tipografi: web memakai Inter/system. Untuk produksi, ganti FontFamily.Default dengan
// Poppins/Inter dari res/font (satu baris) - hierarki ukuran/weight di bawah sudah final.
// Gaya teks dibangun dari bawaan Material3 lewat copy(): konstruktor langsung
// TextStyle bersifat internal pada versi Compose ini (terbukti dari CI).
private val BaseTypo = Typography()
val YgTypography = Typography(
    titleLarge = BaseTypo.titleLarge.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.3).sp),
    titleMedium = BaseTypo.titleMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = BaseTypo.bodyMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = BaseTypo.bodySmall.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, color = YgMuted),
    labelMedium = BaseTypo.labelMedium.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp),
)
val YgShapes = Shapes(extraSmall = RoundedCornerShape(5.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp))
