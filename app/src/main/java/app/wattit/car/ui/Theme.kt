package app.wattit.car.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.wattit.car.R

/** Design tokens from the approved mock: off-white canvas, navy and teal as the only accents. */
object Palette {
    val Frame = Color(0xFF1B1F23)
    val Canvas = Color(0xFFF7F9F9)
    val Line = Color(0xFFE2E6E7)
    val Ink = Color(0xFF111418)
    val Muted = Color(0xFF5F676C)
    val Faint = Color(0xFF8C9498)
    val Navy = Color(0xFF14295A)
    val Teal = Color(0xFF3E8A9A)
    val TealFill = Color(0xFFDCE9EE)
    val BandOther = Color(0xFFB9C0C3)
    val Track = Color(0xFFEDF0F1)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)
