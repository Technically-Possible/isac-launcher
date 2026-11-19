package app.isac.launcher.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.isac.launcher.data.Backdrop
import app.isac.launcher.data.Moon
import app.isac.launcher.data.SkyPhase
import kotlin.math.abs
import kotlin.random.Random

/**
 * The dusk city from design round 2, now alive: the sky follows the real time of day, the current
 * weather plays over the skyline, and the layers drift with the phone's tilt like a hologram.
 * Blurred and shaded behind menus, like the game's menus over the world.
 */
@Composable
fun CityBackdrop(
    mode: Backdrop,
    modifier: Modifier = Modifier,
    tilt: State<Pair<Float, Float>>? = null,
    phase: SkyPhase = SkyPhase.DUSK,
    weatherCode: Int? = null,
    moon: Moon.Phase? = null,
    animate: Boolean = true,
) {
    if (mode == Backdrop.WALLPAPER) {
        // The system wallpaper shows through the window. Darken it so text stays readable.
        Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99060708), Color(0x66060708), Color(0xCC060708)))))
        return
    }
    if (app.isac.launcher.ui.theme.Ink.diamond) {
        VoidBackdrop(modifier, tilt)
        return
    }
    val blurred = mode == Backdrop.CITY_BLURRED
    Box(modifier.fillMaxSize().background(Color(0xFF0C0D0F))) {
        if (blurred) {
            // The blur is expensive, so the blurred scene is drawn once and then only slid around as
            // a single layer when the phone tilts. Nothing inside it changes from frame to frame.
            Box(Modifier.fillMaxSize().parallax(tilt, 14.dp)) {
                Box(Modifier.fillMaxSize().blur(14.dp, BlurredEdgeTreatment.Rectangle)) {
                    SkyLayer(phase, weatherCode, moon, Modifier.fillMaxSize())
                    CityLayer(phase, Modifier.fillMaxSize())
                }
            }
        } else {
            SkyLayer(phase, weatherCode, moon, Modifier.fillMaxSize().parallax(tilt, 7.dp))
            CityLayer(phase, Modifier.fillMaxSize().parallax(tilt, 16.dp))
        }
        if (weatherCode != null && animate) WeatherLayer(weatherCode, Modifier.fillMaxSize())
        val shade = if (blurred) {
            listOf(Color(0x80080909), Color(0xB0080909), Color(0xD0080909))
        } else {
            listOf(Color(0x70000000), Color(0x10000000), Color(0x20000000), Color(0xB0000000))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(shade)))
    }
}

/**
 * DIAMOND's ground: void black, a hairline grid, and a faint nested rhombus that drifts with the
 * phone's tilt. Nothing animates on its own, so it costs nothing while you're not touching the phone.
 */
@Composable
fun VoidBackdrop(modifier: Modifier = Modifier, tilt: State<Pair<Float, Float>>? = null) {
    Box(modifier.fillMaxSize().background(Color(0xFF0B0D10))) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            val grid = Color(0x09F2F5F8)
            var x = 0f
            while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().parallax(tilt, 18.dp)) {
            val c = Offset(size.width * 0.62f, size.height * 0.58f)
            val r = size.width * 0.78f
            val line = Color(0x12F2F5F8)
            drawPath(rhombusPath(c, r), line, style = Stroke(1.dp.toPx()))
            drawPath(rhombusPath(c, r * 0.68f), line, style = Stroke(1.dp.toPx()))
            drawPath(rhombusPath(c, r * 0.36f), Color(0x1CF22D41), style = Stroke(1.dp.toPx()))
            drawLine(Color(0x0CF2F5F8), Offset(0f, c.y), Offset(size.width, c.y), 1f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x00000000), Color(0x99050608)))))
    }
}

/** Moves a layer against the phone's tilt. Reads the tilt while drawing, so nothing recomposes. */
private fun Modifier.parallax(tilt: State<Pair<Float, Float>>?, range: Dp): Modifier =
    if (tilt == null) this else this.graphicsLayer {
        val (x, y) = tilt.value
        translationX = -x * range.toPx()
        translationY = -y * range.toPx()
        scaleX = 1.1f
        scaleY = 1.1f
    }

// ------------------------------------------------------------------ sky

private fun skyStops(phase: SkyPhase): Array<Pair<Float, Color>> = when (phase) {
    SkyPhase.DUSK -> arrayOf(0f to Color(0xFF0E1824), 0.30f to Color(0xFF1A2A3A), 0.47f to Color(0xFF3E4450),
        0.58f to Color(0xFFA8603A), 0.64f to Color(0xFF2A1F1D), 1f to Color(0xFF0C0D0F))
    SkyPhase.DAWN -> arrayOf(0f to Color(0xFF142033), 0.30f to Color(0xFF2C3D55), 0.47f to Color(0xFF6A6A78),
        0.58f to Color(0xFFD08A5E), 0.64f to Color(0xFF2E2420), 1f to Color(0xFF0C0D0F))
    SkyPhase.DAY -> arrayOf(0f to Color(0xFF3B5672), 0.30f to Color(0xFF627D96), 0.47f to Color(0xFF8E9DA8),
        0.58f to Color(0xFFAAA79F), 0.64f to Color(0xFF34312E), 1f to Color(0xFF121214))
    SkyPhase.NIGHT -> arrayOf(0f to Color(0xFF04070C), 0.30f to Color(0xFF09111B), 0.47f to Color(0xFF121A25),
        0.58f to Color(0xFF3A2A22), 0.64f to Color(0xFF151314), 1f to Color(0xFF0A0B0D))
}

private fun glowColor(phase: SkyPhase) = when (phase) {
    SkyPhase.DUSK -> Color(0x99FF8C46)
    SkyPhase.DAWN -> Color(0x88FFB070)
    SkyPhase.DAY -> Color(0x22FFFFFF)
    SkyPhase.NIGHT -> Color(0x55FF7A30) // sodium street-light glow over the city
}

private val CLOUDY = setOf(2, 3, 45, 48)
private fun isRain(c: Int) = c in 51..67 || c in 80..82 || c in 95..99
private fun isSnow(c: Int) = c in 71..77 || c in 85..86

@Composable
private fun SkyLayer(phase: SkyPhase, weatherCode: Int?, moon: Moon.Phase?, modifier: Modifier) {
    val stars = remember {
        val r = Random(73)
        List(90) { Triple(r.nextFloat(), r.nextFloat() * 0.48f, 0.2f + r.nextFloat() * 0.6f) }
    }
    val code = weatherCode ?: 0
    val overcast = code == 3 || isRain(code) || isSnow(code) || code in 45..48
    val cloudy = overcast || code in CLOUDY
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(*skyStops(phase)))
        drawOval(
            Brush.radialGradient(listOf(glowColor(phase), Color.Transparent), center = Offset(w / 2, h * 0.57f), radius = w * 0.75f),
            topLeft = Offset(-w * 0.25f, h * 0.44f), size = Size(w * 1.5f, h * 0.26f),
        )
        if (phase == SkyPhase.NIGHT && !overcast) {
            stars.forEach { (x, y, a) -> drawCircle(Color.White.copy(alpha = a * (if (cloudy) 0.4f else 1f)), 1.2f, Offset(x * w, y * h)) }
            if (moon != null) drawMoonPhase(Offset(w * 0.78f, h * 0.17f), w * 0.045f, moon)
        }
        if (cloudy) {
            // Cloud cover: a grey veil over the upper sky, heavier when overcast.
            val a = if (overcast) 0.55f else 0.3f
            drawRect(Brush.verticalGradient(0f to Color(0xFF2B323B).copy(alpha = a), 0.55f to Color(0xFF2B323B).copy(alpha = a * 0.5f), 0.62f to Color.Transparent))
        }
    }
}

/** The moon in its real phase. Northern hemisphere: a waxing moon is lit on the right. */
fun DrawScope.drawMoonPhase(center: Offset, r: Float, m: Moon.Phase) {
    val lit = Color(0xFFE9E5DD)
    val dark = Color(0xFF1A1D22)
    drawCircle(lit.copy(alpha = 0.12f), r * 2.2f, center)
    drawCircle(dark, r, center)
    val circle = Path().apply { addOval(Rect(center, r)) }
    clipPath(circle) {
        val k = m.illumination.toFloat()
        drawArc(lit, if (m.waxing) -90f else 90f, 180f, true, topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r))
        val rx = r * abs(1 - 2 * k)
        drawOval(if (k < 0.5f) dark else lit, topLeft = Offset(center.x - rx, center.y - r), size = Size(2 * rx, 2 * r))
    }
}

// ------------------------------------------------------------------ city

private class Light(val x: Float, val y: Float, val size: Float, val alpha: Float, val warm: Boolean)

/** Building outline as (x%, y%) of the skyline box, which fills the bottom 47% of the screen. */
private val SKYLINE = listOf(
    0f to 22f, 8f to 22f, 8f to 12f, 16f to 12f, 16f to 26f, 26f to 26f, 26f to 16f, 33f to 16f, 33f to 24f,
    44f to 24f, 44f to 10f, 47f to 10f, 47f to 6f, 53f to 6f, 53f to 10f, 56f to 10f, 56f to 24f, 66f to 24f,
    66f to 8f, 74f to 8f, 74f to 20f, 84f to 20f, 84f to 14f, 92f to 14f, 92f to 28f, 100f to 28f,
)

private fun roofAt(x: Float): Float {
    var y = SKYLINE.first().second
    for (i in 0 until SKYLINE.size - 1) {
        val (x0, y0) = SKYLINE[i]
        val (x1, _) = SKYLINE[i + 1]
        if (x >= x0 && x < x1) y = y0
        if (x0 == x1 && x >= x0) y = SKYLINE[i + 1].second
    }
    return y
}

@Composable
private fun CityLayer(phase: SkyPhase, modifier: Modifier) {
    val lights = remember {
        val r = Random(1593)
        List(260) {
            val x = r.nextFloat() * 100f
            val top = roofAt(x) + 3f
            val y = top + r.nextFloat() * (72f - top)
            Light(x, y, 0.8f + r.nextFloat() * 1.4f, 0.25f + r.nextFloat() * 0.7f, r.nextFloat() > 0.15f)
        }
    }
    val lightLevel = when (phase) { SkyPhase.NIGHT -> 1.15f; SkyPhase.DUSK, SkyPhase.DAWN -> 0.85f; SkyPhase.DAY -> 0.18f }
    val building = if (phase == SkyPhase.DAY) Color(0xFF1C1F24) else Color(0xFF0A0B0D)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val boxTop = h * 0.53f
        val boxH = h * 0.47f
        fun px(xp: Float) = w * xp / 100f
        fun py(yp: Float) = boxTop + boxH * yp / 100f
        val city = Path().apply {
            moveTo(0f, py(SKYLINE.first().second))
            SKYLINE.forEach { (x, y) -> lineTo(px(x), py(y)) }
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(city, building)
        val roof = py(6f)
        val domeW = w * 0.16f
        val domeH = h * 0.04f
        drawRect(building, topLeft = Offset(w / 2 - domeW * 0.36f, roof - domeH * 0.45f), size = Size(domeW * 0.72f, domeH * 0.5f))
        drawArc(building, 180f, 180f, true, topLeft = Offset(w / 2 - domeW / 2, roof - domeH * 1.4f), size = Size(domeW, domeH * 1.9f))
        drawRect(building, topLeft = Offset(w / 2 - 2.dp.toPx(), roof - domeH * 1.4f - 14.dp.toPx()), size = Size(4.dp.toPx(), 16.dp.toPx()))
        lights.forEach { l ->
            val a = (l.alpha * lightLevel).coerceAtMost(1f)
            val c = if (l.warm) Color(1f, 0.77f, 0.47f, a * 0.8f) else Color(0.78f, 0.86f, 1f, a * 0.5f)
            val s = l.size.dp.toPx()
            drawRect(c, topLeft = Offset(px(l.x), py(l.y)), size = Size(s, s * 1.3f))
        }
        listOf(0.18f to 0.86f, 0.42f to 0.92f, 0.71f to 0.88f, 0.9f to 0.95f).forEach { (gx, gy) ->
            drawCircle(
                Brush.radialGradient(listOf(Color(0x66FFA050).copy(alpha = 0.4f * lightLevel), Color.Transparent), center = Offset(w * gx, h * gy), radius = w * 0.14f),
                radius = w * 0.14f, center = Offset(w * gx, h * gy),
            )
        }
    }
}

// ------------------------------------------------------------------ weather

/** Rain, snow, fog or a thunderstorm over the city, from the current weather code. */
@Composable
private fun WeatherLayer(code: Int, modifier: Modifier) {
    val rain = isRain(code)
    val snow = isSnow(code)
    val fog = code == 45 || code == 48
    val storm = code in 95..99
    if (!rain && !snow && !fog) return
    val drops = remember(rain, snow) {
        val r = Random(7)
        List(if (rain) 110 else 80) { Triple(r.nextFloat(), r.nextFloat(), 0.7f + r.nextFloat() * 0.6f) }
    }
    val transition = rememberInfiniteTransition(label = "weather")
    val t = transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (snow) 9_000 else 1_000, easing = LinearEasing), RepeatMode.Restart), label = "fall",
    )
    val flash = if (storm) transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(7_000, easing = LinearEasing), RepeatMode.Restart), label = "flash",
    ) else null
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (fog) {
            drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 0.7f to Color(0x33C8CDD2), 1f to Color(0x55C8CDD2)))
        }
        val p = t.value
        if (rain) {
            val len = 18.dp.toPx()
            drops.forEach { (x, y0, speed) ->
                val y = ((y0 + p * speed) % 1f) * (h + len) - len
                val xx = x * w + y * 0.12f
                drawLine(Color(0x55B9C6D2), Offset(xx, y), Offset(xx + len * 0.18f, y + len), strokeWidth = 1.3f)
            }
        }
        if (snow) {
            drops.forEach { (x, y0, speed) ->
                val y = ((y0 + p * speed) % 1f) * h
                val sway = kotlin.math.sin((p * 6.28f * speed) + x * 20f) * 8.dp.toPx()
                drawCircle(Color(0xAAFFFFFF), 1.6f + speed, Offset(x * w + sway, y))
            }
        }
        val f = flash?.value
        if (f != null && (f in 0.30f..0.32f || f in 0.35f..0.36f)) {
            drawRect(Color(0x40DDE6FF))
        }
    }
}
