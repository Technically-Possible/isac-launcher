package app.isac.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons drawn for this app, on a 24-unit grid. Tint them with ColorFilter.tint. */
object IsacIcons {
    private fun c(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun line(name: String, vararg paths: String, width: Float = 1.7f, filled: Set<Int> = emptySet()) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEachIndexed { i, d ->
                if (i in filled) {
                    addPath(addPathNodes(d), fill = SolidColor(Color.White))
                } else {
                    addPath(
                        addPathNodes(d), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = width,
                        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
        }.build()

    val Search = line("search", c(11f, 11f, 6.5f), "M20 20l-4.3-4.3", width = 2f)
    val Torch = line("torch", "M7 3h10l-1.5 6h-7z", "M8.5 9h7v3l-1.5 9h-4l-1.5-9z", "M12 13v3")
    val Pulse = line("pulse", c(12f, 12f, 2f), "M7.8 7.8a6 6 0 0 0 0 8.4M16.2 7.8a6 6 0 0 1 0 8.4M5 5a10 10 0 0 0 0 14M19 5a10 10 0 0 1 0 14")
    val Battery = line("battery", "M4.5 7h13a1.5 1.5 0 0 1 1.5 1.5v7a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 3 15.5v-7A1.5 1.5 0 0 1 4.5 7z", "M21 10.5v3", "M5 9h9v6H5z", filled = setOf(2))
    val Box = line("box", "M3.5 7.5L12 3l8.5 4.5v9L12 21l-8.5-4.5z", "M3.5 7.5L12 12l8.5-4.5M12 12v9")
    val Brief = line("brief", "M9 4h6a1 1 0 0 1 1 1v2h4a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1h4V5a1 1 0 0 1 1-1zm1 2v1h4V6z", filled = setOf(0))
    val Chevron = line("chevron", "M9 5l7 7-7 7", width = 2f)
    val Photo = line("photo", "M4.5 4h15A1.5 1.5 0 0 1 21 5.5v13a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5v-13A1.5 1.5 0 0 1 4.5 4z", "M3 17l5-5 4 4 3-3 6 6", c(15.5f, 8.5f, 1.5f))
    val Gear = line("gear", c(12f, 12f, 3f), c(12f, 12f, 6.5f), "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1")
    val Grid = line("grid", "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z")
    val Play = line("play", "M8 5l11 7-11 7z", filled = setOf(0))
    val Pause = line("pause", "M8 5v14M16 5v14", width = 3f)
    val Next = line("next", "M5 5l10 7-10 7z", "M18 5v14", filled = setOf(0), width = 2.4f)
    val Prev = line("prev", "M19 5l-10 7 10 7z", "M6 5v14", filled = setOf(0), width = 2.4f)
    val Plus = line("plus", "M12 5v14M5 12h14", width = 2f)

    // weather
    val Sun = line("sun", c(12f, 12f, 4f), "M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3l1.8 1.8M16.9 16.9l1.8 1.8M5.3 18.7l1.8-1.8M16.9 7.1l1.8-1.8")
    val Moon = line("moon", "M15 3.5a8.5 8.5 0 1 0 5.5 13 7 7 0 0 1-5.5-13z")
    val Cloud = line("cloud", "M7 18h10a4 4 0 0 0 .4-8 5.5 5.5 0 0 0-10.6 1.6A3.2 3.2 0 0 0 7 18z")
    val CloudSun = line("cloudsun", "M8 4.5v1.5M3.8 6.3l1 1M2.5 10.5H4", "M5.6 11.2a3.5 3.5 0 0 1 6.2-2.8", "M8 20h9.5a3.5 3.5 0 0 0 .3-7 5 5 0 0 0-9.6 1.4A2.8 2.8 0 0 0 8 20z")
    val CloudMoon = line("cloudmoon", "M13.5 3a5 5 0 0 0 6.5 6.6A5.6 5.6 0 0 1 13.5 3z", "M6 20.5h9a3.3 3.3 0 0 0 .3-6.6 4.7 4.7 0 0 0-9 1.3A2.7 2.7 0 0 0 6 20.5z")
    val Rain = line("rain", "M7 15h10a4 4 0 0 0 .4-8 5.5 5.5 0 0 0-10.6 1.6A3.2 3.2 0 0 0 7 15z", "M8 18l-1 2.5M12 18l-1 2.5M16 18l-1 2.5")
    val Snow = line("snow", "M7 15h10a4 4 0 0 0 .4-8 5.5 5.5 0 0 0-10.6 1.6A3.2 3.2 0 0 0 7 15z", "M8 19h.01M12 20.5h.01M16 19h.01", width = 2.2f)
    val Fog = line("fog", "M4 9h16M6 13h12M4 17h16")
    val Storm = line("storm", "M7 14h10a4 4 0 0 0 .4-8 5.5 5.5 0 0 0-10.6 1.6A3.2 3.2 0 0 0 7 14z", "M12.5 14l-2 4h3l-2 4")

    fun weather(code: Int, isDay: Boolean): ImageVector = when (code) {
        0, 1 -> if (isDay) Sun else Moon
        2 -> if (isDay) CloudSun else CloudMoon
        3 -> Cloud
        45, 48 -> Fog
        in 51..67, 80, 81, 82 -> Rain
        in 71..77, 85, 86 -> Snow
        95, 96, 99 -> Storm
        else -> Cloud
    }
}
