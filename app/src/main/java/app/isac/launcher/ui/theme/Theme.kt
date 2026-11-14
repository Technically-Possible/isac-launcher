package app.isac.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.isac.launcher.R
import app.isac.launcher.data.Rarity

object Ink {
    val Orange = Color(0xFFFF6D10)
    val OrangeDeep = Color(0xFFC8520A)
    val White = Color(0xFFF2F2F0)
    val G2 = White.copy(alpha = 0.64f)
    val G3 = White.copy(alpha = 0.42f)
    val Line = White.copy(alpha = 0.12f)
    val Ground = Color(0xFF0B0C0D)
    val Glass = Color(0xC00A0B0D)
    val GlassDeep = Color(0xEE0C0D0F)
    val OnOrange = Color(0xFF140A04)
    val Crit = Color(0xFFE8433A)
    val Online = Color(0xFF4FBF72)

    // attribute colors
    val AttrRed = Color(0xFFE04848)
    val AttrBlue = Color(0xFF3B8FE6)
    val AttrYellow = Color(0xFFF0C23B)

    fun rarity(r: Rarity): Color = when (r) {
        Rarity.EXOTIC -> Color(0xFFE0573B)
        Rarity.HIGH_END -> Color(0xFFE8B923)
        Rarity.GEAR_SET -> Color(0xFF2BBFA4)
        Rarity.SUPERIOR -> Color(0xFF9B62E3)
        Rarity.SPECIALIZED -> Color(0xFF3D8EE0)
        Rarity.STANDARD -> Color(0xFF6DBB45)
        Rarity.WORN -> Color(0xFFB9B9B9)
    }
}

/** Titillium Web: a free, open-licensed stand-in for Borda, the game's UI typeface. */
val Titillium = FontFamily(
    Font(R.font.titillium_light, FontWeight.Light),
    Font(R.font.titillium_regular, FontWeight.Normal),
    Font(R.font.titillium_semibold, FontWeight.SemiBold),
    Font(R.font.titillium_bold, FontWeight.Bold),
)

object Type {
    fun label(size: TextUnit = 11.sp, color: Color = Ink.Orange) = TextStyle(
        fontFamily = Titillium, fontWeight = FontWeight.SemiBold, fontSize = size,
        letterSpacing = 0.18.em, color = color, lineHeight = size * 1.1f,
    )

    fun title(size: TextUnit = 18.sp, color: Color = Ink.White) = TextStyle(
        fontFamily = Titillium, fontWeight = FontWeight.Bold, fontSize = size,
        letterSpacing = 0.06.em, color = color, lineHeight = size * 1.05f,
    )

    fun body(size: TextUnit = 14.sp, color: Color = Ink.White, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = Titillium, fontWeight = weight, fontSize = size, color = color, lineHeight = size * 1.35f,
    )

    /** Large light numbers. The font's tall built-in line spacing is trimmed so digits sit tight. */
    fun big(size: TextUnit, color: Color = Ink.White) = TextStyle(
        fontFamily = Titillium, fontWeight = FontWeight.Light, fontSize = size,
        color = color, lineHeight = size * 0.82f, letterSpacing = (-0.01).em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}
