package app.isac.launcher.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.isac.launcher.R
import app.isac.launcher.data.Faction
import app.isac.launcher.data.Rarity

/**
 * The interface's look. SHD is ISAC: warm orange, rings, human in the loop. DIAMOND is Black Tusk:
 * crimson and ice white on black, rhombus shapes, angular and cold.
 */
enum class Skin { SHD, DIAMOND }

fun Faction.skin(): Skin = if (this == Faction.BLACK_TUSK) Skin.DIAMOND else Skin.SHD

object Ink {
    private val skinState = mutableStateOf(Skin.SHD)
    private val accent = mutableStateOf(Color(0xFFFF6D10))

    /** The current look. Reading it (or any colour below) recomposes when it changes. */
    val skin: Skin get() = skinState.value
    val diamond: Boolean get() = skinState.value == Skin.DIAMOND

    fun setSkin(s: Skin) {
        skinState.value = s
    }

    fun setAccent(argb: Long) {
        accent.value = Color(argb)
    }

    /**
     * DIAMOND's Tactical Crimson, tuned slightly brighter than the spec's #E6192E so that small
     * crimson text still reads on black (4.5:1 or better).
     */
    val Crimson = Color(0xFFF22D41)
    val Cobalt = Color(0xFF00A8E8)
    val Amber = Color(0xFFFF9E00)

    /** The accent: SHD orange (or an unlocked accent), or crimson under DIAMOND. */
    val Orange: Color get() = if (diamond) Crimson else accent.value

    val OrangeDeep: Color get() = if (diamond) Color(0xFFA50F20) else Color(0xFFC8520A)
    val White: Color get() = if (diamond) Color(0xFFF2F5F8) else Color(0xFFF2F2F0)
    val G2: Color get() = White.copy(alpha = 0.64f)
    val G3: Color get() = White.copy(alpha = 0.42f)
    val Line: Color get() = White.copy(alpha = 0.12f)
    val Ground: Color get() = if (diamond) Color(0xFF0B0D10) else Color(0xFF0B0C0D)
    val Glass: Color get() = if (diamond) Color(0xE6101318) else Color(0xC00A0B0D)
    val GlassDeep: Color get() = if (diamond) Color(0xF50E1115) else Color(0xEE0C0D0F)
    val OnOrange: Color get() = if (diamond) Color(0xFF0B0D10) else Color(0xFF140A04)

    /** Critical warnings. DIAMOND keeps red for itself, so its warnings are amber. */
    val Crit: Color get() = if (diamond) Amber else Color(0xFFE8433A)
    val Online: Color get() = if (diamond) Cobalt else Color(0xFF4FBF72)

    // attribute colors
    val AttrRed = Color(0xFFE04848)
    val AttrBlue = Color(0xFF3B8FE6)
    val AttrYellow = Color(0xFFF0C23B)

    fun rarity(r: Rarity): Color = if (diamond) {
        when (r) {
            Rarity.EXOTIC -> Crimson
            Rarity.HIGH_END, Rarity.GEAR_SET -> Cobalt
            Rarity.SUPERIOR, Rarity.SPECIALIZED -> Color(0xFFF2F5F8)
            Rarity.STANDARD -> Color(0xFF5C6470)
            Rarity.WORN -> Color(0xFF3A3F47)
        }
    } else {
        when (r) {
            Rarity.EXOTIC -> Color(0xFFE0573B)
            Rarity.HIGH_END -> Color(0xFFE8B923)
            Rarity.GEAR_SET -> Color(0xFF2BBFA4)
            Rarity.SUPERIOR -> Color(0xFF9B62E3)
            Rarity.SPECIALIZED -> Color(0xFF3D8EE0)
            Rarity.STANDARD -> Color(0xFF6DBB45)
            Rarity.WORN -> Color(0xFFB9B9B9)
        }
    }
}

/** Titillium Web: a free, open-licensed stand-in for Borda, the game's UI typeface. */
val Titillium = FontFamily(
    Font(R.font.titillium_light, FontWeight.Light),
    Font(R.font.titillium_regular, FontWeight.Normal),
    Font(R.font.titillium_semibold, FontWeight.SemiBold),
    Font(R.font.titillium_bold, FontWeight.Bold),
)

/** DIAMOND: Chakra Petch for labels and reading text; its cut corners suit the angular look. */
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** DIAMOND: wide geometric headers, in the spirit of Eurostile Extended. */
val Michroma = FontFamily(Font(R.font.michroma, FontWeight.Normal))

/** DIAMOND: telemetry, times and counts. One variable font file covers every weight. */
@OptIn(ExperimentalTextApi::class)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jetbrains_mono, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.jetbrains_mono, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

object Type {
    private val ui: FontFamily get() = if (Ink.diamond) ChakraPetch else Titillium

    fun label(size: TextUnit = 11.sp, color: Color = Ink.Orange) = TextStyle(
        fontFamily = ui, fontWeight = if (Ink.diamond) FontWeight.Bold else FontWeight.SemiBold, fontSize = size,
        letterSpacing = if (Ink.diamond) 0.14.em else 0.18.em, color = color, lineHeight = size * 1.1f,
    )

    /** Under DIAMOND, big headings (22sp and up) use the wide Michroma face; smaller ones stay compact. */
    fun title(size: TextUnit = 18.sp, color: Color = Ink.White): TextStyle =
        if (Ink.diamond && size.value >= 22f) {
            TextStyle(
                fontFamily = Michroma, fontWeight = FontWeight.Normal, fontSize = size * 0.8f,
                letterSpacing = 0.1.em, color = color, lineHeight = size * 1.0f,
            )
        } else {
            TextStyle(
                fontFamily = ui, fontWeight = FontWeight.Bold, fontSize = size,
                letterSpacing = 0.06.em, color = color, lineHeight = size * 1.05f,
            )
        }

    fun body(size: TextUnit = 14.sp, color: Color = Ink.White, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = ui, fontWeight = weight, fontSize = size, color = color, lineHeight = size * 1.35f,
    )

    /** Large numbers. The font's tall built-in line spacing is trimmed so digits sit tight. */
    fun big(size: TextUnit, color: Color = Ink.White): TextStyle = if (Ink.diamond) {
        TextStyle(
            fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = size * 0.86f,
            color = color, lineHeight = size * 0.8f, letterSpacing = (-0.03).em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    } else {
        TextStyle(
            fontFamily = Titillium, fontWeight = FontWeight.Light, fontSize = size,
            color = color, lineHeight = size * 0.82f, letterSpacing = (-0.01).em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }

    /** The wide geometric face under DIAMOND (Michroma, for callsigns and headers); a normal title under SHD. */
    fun wide(size: TextUnit, color: Color = Ink.White): TextStyle = if (Ink.diamond) {
        TextStyle(
            fontFamily = Michroma, fontWeight = FontWeight.Normal, fontSize = size, letterSpacing = 0.1.em,
            color = color, lineHeight = size * 1.15f, platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    } else title(size, color)

    /** The big day of the month: Michroma under DIAMOND, light Titillium under SHD. */
    fun day(size: TextUnit, color: Color = Ink.White): TextStyle = if (Ink.diamond) {
        TextStyle(
            fontFamily = Michroma, fontWeight = FontWeight.Normal, fontSize = size * 0.74f, letterSpacing = (-0.02).em,
            color = color, lineHeight = size * 0.74f,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    } else big(size, color)

    /** Telemetry text: monospace under DIAMOND, the normal label face under SHD. */
    fun mono(size: TextUnit = 11.sp, color: Color = Ink.White, weight: FontWeight = FontWeight.Medium) = TextStyle(
        fontFamily = if (Ink.diamond) JetBrainsMono else Titillium, fontWeight = weight, fontSize = size,
        letterSpacing = 0.04.em, color = color, lineHeight = size * 1.2f,
    )
}

/** Words that change with the faction, so DIAMOND speaks Black Tusk rather than SHD. */
object Lex {
    private val d: Boolean get() = Ink.diamond

    /** The AI's name. */
    val ai: String get() = if (d) "DIAMOND" else "ISAC"
    val brand: String get() = if (d) "B.T.S.U." else "SHD ISAC"
    val inventory: String get() = if (d) "ARMORY" else "INVENTORY"
    val map: String get() = if (d) "SECTOR" else "MAP"
    val directives: String get() = if (d) "OBJECTIVES" else "DIRECTIVES"
    val armor: String get() = if (d) "PLATING" else "ARMOR"
    val junk: String get() = if (d) "SCRAP" else "JUNK"
    val destination: String get() = if (d) "DEPLOYMENT" else "DESTINATION"
    val pulse: String get() = if (d) "DIAMOND CORE" else "PULSE"
    val agent: String get() = if (d) "Operator" else "Agent"
    val levelWord: String get() = if (d) "Tier" else "Level"

    fun level(n: Int): String = if (d) "TIER %02d".format(n) else "LEVEL $n"

    fun rarity(r: Rarity): String = if (!d) r.label else when (r) {
        Rarity.EXOTIC -> "CLASSIFIED"
        Rarity.HIGH_END, Rarity.GEAR_SET -> "PROTOTYPE"
        Rarity.SUPERIOR, Rarity.SPECIALIZED -> "MILSPEC"
        Rarity.STANDARD -> "STANDARD"
        Rarity.WORN -> "DECOMMISSIONED"
    }

    /** Rewrites a sentence written for ISAC into DIAMOND's terms. */
    fun adapt(text: String): String = if (!d) text else text
        .replace("ISAC", "DIAMOND")
        .replace("Inventory", "Armory").replace("inventory", "armory")
        .replace("Directives", "Objectives").replace("directives", "objectives")
        .replace("junk", "scrap").replace("Junk", "Scrap")
        .replace("Worn", "Decommissioned")
        .replace("Exotic", "Classified")
}
