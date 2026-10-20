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
val YgTypography = Typography(
    titleLarge = TextStyle(FontFamily.Default, FontWeight.Bold, 19.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(FontFamily.Default, FontWeight.SemiBold, 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(FontFamily.Default, FontWeight.Normal, 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(FontFamily.Default, FontWeight.Normal, 12.5.sp, color = YgMuted),
    labelMedium = TextStyle(FontFamily.Default, FontWeight.Medium, 13.sp),
)
val YgShapes = Shapes(extraSmall = RoundedCornerShape(5.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp))
