package app.isac.launcher.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.isac.launcher.ui.theme.Ink

/** DIAMOND's panel shape: the top-left and bottom-right corners cut at 45 degrees. */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2)
        return Outline.Generic(
            Path().apply {
                moveTo(c, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - c)
                lineTo(size.width - c, size.height); lineTo(0f, size.height); lineTo(0f, c); close()
            },
        )
    }
}

/** A square turned 45 degrees: DIAMOND's signature shape. */
object RhombusShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(rhombusPath(Offset(size.width / 2, size.height / 2), size.width / 2, size.height / 2))
}

fun rhombusPath(c: Offset, rx: Float, ry: Float = rx) = Path().apply {
    moveTo(c.x, c.y - ry); lineTo(c.x + rx, c.y); lineTo(c.x, c.y + ry); lineTo(c.x - rx, c.y); close()
}

/** A panel's corner cut under DIAMOND, or square corners under SHD. */
fun panelShape(cut: Dp = 10.dp): Shape = if (Ink.diamond) ChamferShape(cut) else RectangleShape

/** Round under SHD, a rhombus under DIAMOND: skill buttons, markers and badges. */
fun markerShape(): Shape = if (Ink.diamond) RhombusShape else CircleShape

/** A range ring: a circle under SHD, a rhombus under DIAMOND. */
fun DrawScope.ring(color: Color, radius: Float, center: Offset = this.center, width: Float = 1.dp.toPx()) {
    if (Ink.diamond) drawPath(rhombusPath(center, radius), color, style = Stroke(width))
    else drawCircle(color, radius, center, style = Stroke(width))
}

/** A filled marker dot: round under SHD, a rhombus under DIAMOND. */
fun DrawScope.dot(color: Color, radius: Float, center: Offset) {
    if (Ink.diamond) drawPath(rhombusPath(center, radius * 1.25f), color)
    else drawCircle(color, radius, center)
}

/** DIAMOND's heavy corner brackets on two opposite corners of a box. */
fun DrawScope.cornerBrackets(color: Color, length: Float = 12.dp.toPx(), width: Float = 2.5.dp.toPx()) {
    val w = size.width
    val h = size.height
    drawRect(color, Offset(w - length, 0f), Size(length, width))
    drawRect(color, Offset(w - width, 0f), Size(width, length))
    drawRect(color, Offset(0f, h - width), Size(length, width))
    drawRect(color, Offset(0f, h - length), Size(width, length))
}
