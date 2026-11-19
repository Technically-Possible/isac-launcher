package app.isac.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.Type
import kotlin.math.roundToInt

private val POINTS = mapOf(0 to "N", 45 to "NE", 90 to "E", 135 to "SE", 180 to "S", 225 to "SW", 270 to "W", 315 to "NW")

fun compassWord(deg: Float): String {
    val names = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    return names[(((deg % 360) + 360) % 360 / 22.5f).roundToInt() % 16]
}

/**
 * A compass strip like the game's: ticks every 5°, N/NE/E… labels with north in orange, the heading
 * underneath, and an orange waypoint diamond for the Base of Operations.
 */
@Composable
fun CompassStrip(
    heading: Float?,
    waypointBearing: Float?,
    waypointLabel: String?,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = Type.label(10.sp, Ink.White)
    val northStyle = Type.label(10.sp, Ink.Orange)
    val readStyle = Type.label(10.sp, Ink.Orange)
    val wpStyle = Type.label(9.sp, Ink.Orange)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val w = size.width
        val mid = w / 2
        val visible = 150f
        val ppd = w / visible
        val tickBase = 22.dp.toPx()
        if (heading == null) {
            val t = measurer.measure("NO COMPASS", labelStyle.copy(color = Ink.G3))
            drawText(t, topLeft = Offset(mid - t.size.width / 2, tickBase - t.size.height))
            return@Canvas
        }
        val start = ((heading - visible / 2) / 5f).toInt() * 5 - 5
        var d = start
        while (d <= heading + visible / 2 + 5) {
            val x = mid + (d - heading) * ppd
            val norm = ((d % 360) + 360) % 360
            val label = POINTS[norm]
            val len = when { label != null -> 9.dp.toPx(); norm % 15 == 0 -> 6.dp.toPx(); else -> 3.5.dp.toPx() }
            drawLine(Ink.White.copy(alpha = if (label != null) 0.9f else 0.45f), Offset(x, tickBase - len), Offset(x, tickBase), strokeWidth = 1.2.dp.toPx())
            if (label != null) {
                val t = measurer.measure(label, if (norm == 0) northStyle else labelStyle)
                drawText(t, topLeft = Offset(x - t.size.width / 2, 0f))
            }
            d += 5
        }
        drawLine(Ink.Line, Offset(0f, tickBase), Offset(w, tickBase), strokeWidth = 1f)

        // Base of Operations waypoint: on the strip if it's in view, otherwise pinned to the nearer edge.
        if (waypointBearing != null) {
            var rel = waypointBearing - heading
            while (rel > 180) rel -= 360
            while (rel < -180) rel += 360
            val edge = 10.dp.toPx()
            val x = (mid + rel * ppd).coerceIn(edge, w - edge)
            val s = 5.dp.toPx()
            val y = tickBase - 4.dp.toPx()
            val diamond = Path().apply {
                moveTo(x, y - s); lineTo(x + s, y); lineTo(x, y + s); lineTo(x - s, y); close()
            }
            drawPath(diamond, Ink.Orange)
            if (waypointLabel != null) {
                val t = measurer.measure(waypointLabel, wpStyle)
                val tx = (x - t.size.width / 2).coerceIn(0f, w - t.size.width)
                // Below the strip, unless that would sit on top of the heading readout.
                val clearOfReadout = kotlin.math.abs(x - mid) > t.size.width / 2 + 34.dp.toPx()
                drawText(t, topLeft = Offset(tx, if (clearOfReadout) tickBase + 2.dp.toPx() else 0f))
            }
        }

        // Centre marker and heading readout.
        if (Ink.diamond) {
            // DIAMOND: a rhombus needle sitting on the line.
            drawPath(rhombusPath(Offset(mid, tickBase), 5.dp.toPx()), Ink.Orange)
        } else {
            val tri = Path().apply {
                moveTo(mid - 5.dp.toPx(), tickBase + 7.dp.toPx())
                lineTo(mid + 5.dp.toPx(), tickBase + 7.dp.toPx())
                lineTo(mid, tickBase + 1.dp.toPx())
                close()
            }
            drawPath(tri, Ink.Orange)
        }
        val read = measurer.measure(
            if (Ink.diamond) "HDG ${heading.roundToInt() % 360}°" else "${heading.roundToInt() % 360}° ${compassWord(heading)}",
            if (Ink.diamond) Type.mono(10.sp, Ink.Orange, androidx.compose.ui.text.font.FontWeight.Bold) else readStyle,
        )
        drawText(read, topLeft = Offset(mid - read.size.width / 2, size.height - read.size.height + 2.dp.toPx()))

        // Fade both ends of the strip.
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
            size = Size(w, size.height), blendMode = BlendMode.DstIn,
        )
    }
}
