package app.isac.launcher.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.isac.launcher.data.AppEntry
import app.isac.launcher.data.AppIcon
import app.isac.launcher.data.IconStyle
import app.isac.launcher.data.Rarity
import app.isac.launcher.graph
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type
import androidx.compose.ui.platform.LocalContext

// ---------------------------------------------------------------- text

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Ink.Orange, size: Float = 11f) {
    BasicText(text.uppercase(), modifier, style = Type.label(size.sp, color), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Txt(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(text, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

// ---------------------------------------------------------------- clicks

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tap(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier =
    this.combinedClickable(
        interactionSource = null,
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick,
    )

// ---------------------------------------------------------------- surfaces

/** Dark glass panel with an orange top edge and orange corner ticks. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 12.dp,
    fill: Color = Ink.Glass,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .background(fill)
            .border(1.dp, Ink.Line)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRect(Ink.Orange.copy(alpha = 0.75f), size = Size(size.width, w))
                val tick = 10.dp.toPx()
                drawRect(Ink.Orange, topLeft = Offset(0f, size.height - w), size = Size(tick, w))
                drawRect(Ink.Orange, topLeft = Offset(size.width - tick, size.height - w), size = Size(tick, w))
            }
            .padding(padding),
        content = content,
    )
}

/** Orange section header with a rule, like WEAPONS ───── 3/3 */
@Composable
fun SectionHeader(title: String, count: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(Ink.Orange))
        Spacer(Modifier.width(8.dp))
        Label(title)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Ink.Orange.copy(alpha = 0.35f)))
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            Label(count, color = Ink.G2)
        }
    }
}

/** Item tile: dark fill, rarity glow from the bottom, rarity strip, orange outline when selected. */
@Composable
fun Tile(
    rarity: Rarity,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val rc = Ink.rarity(rarity)
    Box(
        modifier
            .then(if (selected) Modifier.border(1.5.dp, Ink.Orange) else Modifier.border(1.dp, Ink.Line))
            .background(Brush.verticalGradient(0.2f to Color.Transparent, 1f to rc.copy(alpha = 0.32f)))
            .background(Color(0xB8101113))
            .drawBehind {
                val h = 2.dp.toPx()
                drawRect(rc, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            }
            .tap(onClick, onLongClick),
        content = content,
    )
}

/** Orange notification counter for the corner of an app tile. Shows nothing when [count] is 0. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
            .background(Ink.Orange)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Txt(if (count > 99) "99+" else count.toString(), Type.title(10.sp, Ink.OnOrange))
    }
}

/** Orange key chip used in the prompt bar, like a controller button prompt. */
@Composable
fun KeyChip(icon: ImageVector? = null, text: String? = null) {
    Box(Modifier.size(18.dp).background(Ink.Orange), contentAlignment = Alignment.Center) {
        if (icon != null) Glyph(icon, 12.dp, Ink.OnOrange)
        else if (text != null) Txt(text, Type.title(11.sp, Ink.OnOrange))
    }
}

data class Prompt(
    val label: String,
    val icon: ImageVector? = null,
    val key: String? = null,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

@Composable
fun PromptBar(prompts: List<Prompt>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0x99000000))
            .drawBehind { drawRect(Ink.Orange.copy(alpha = 0.45f), size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        prompts.forEach { p ->
            Row(Modifier.tap(p.onClick, p.onLongClick), verticalAlignment = Alignment.CenterVertically) {
                KeyChip(p.icon, p.key)
                Spacer(Modifier.width(7.dp))
                Label(p.label, color = Ink.White, size = 11f)
            }
        }
    }
}

// ---------------------------------------------------------------- tabs

@Composable
fun IsacTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .drawBehind { drawRect(Ink.Line, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Bumper("‹") { onSelect((selected - 1).coerceAtLeast(0)) }
        titles.forEachIndexed { i, t -> TabLabel(t, i == selected) { onSelect(i) } }
        Bumper("›") { onSelect((selected + 1).coerceAtMost(titles.lastIndex)) }
    }
}

@Composable
private fun TabLabel(title: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .tap(onClick)
            .drawBehind {
                if (on) drawRect(Ink.Orange, topLeft = Offset(0f, size.height - 2.5.dp.toPx()), size = Size(size.width, 2.5.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Label(title, color = if (on) Ink.White else Ink.G3, size = 13f)
    }
}

/**
 * Tabs at the very top of the screen, laid out around the camera cutout like the status bar:
 * the first half of the tabs sit left of the hole, the rest sit right of it.
 * [cutoutLeft] and [cutoutRight] are the cutout's edges measured from the left of the screen.
 */
@Composable
fun CutoutTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    cutoutLeft: Dp,
    cutoutRight: Dp,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
) {
    val split = (titles.size + 1) / 2
    val side = 14.dp
    val margin = 10.dp
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                drawRect(Ink.Line, topLeft = Offset(side.toPx(), size.height - 1.dp.toPx()), size = Size(size.width - 2 * side.toPx(), 1.dp.toPx()))
            },
    ) {
        val leftWidth = (cutoutLeft - margin - side).coerceAtLeast(0.dp)
        val rightWidth = (maxWidth - cutoutRight - margin - side).coerceAtLeast(0.dp)
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = side).width(leftWidth).fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bumper("‹") { onSelect((selected - 1).coerceAtLeast(0)) }
            titles.take(split).forEachIndexed { i, t -> TabLabel(t, i == selected) { onSelect(i) } }
        }
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = side).width(rightWidth).fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            titles.drop(split).forEachIndexed { j, t -> TabLabel(t, split + j == selected) { onSelect(split + j) } }
            Bumper("›") { onSelect((selected + 1).coerceAtMost(titles.lastIndex)) }
        }
    }
}

/** Left and right edges of a camera cutout at the top of the screen, or null if there isn't one. */
@Composable
fun topCutout(): Pair<Dp, Dp>? {
    val context = LocalContext.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(config.orientation, config.screenWidthDp) {
        if (android.os.Build.VERSION.SDK_INT < 29) return@remember null
        val display = if (android.os.Build.VERSION.SDK_INT >= 30) context.display else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay
        }
        val r = runCatching { display?.cutout?.boundingRectTop }.getOrNull()
        if (r == null || r.isEmpty) null else with(density) { r.left.toDp() to r.right.toDp() }
    }
}

@Composable
private fun Bumper(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 24.dp, height = 20.dp).border(1.dp, Ink.Orange.copy(alpha = 0.8f)).tap(onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(glyph, Type.title(14.sp, Ink.Orange)) }
}

// ---------------------------------------------------------------- bars

/** Segmented armor bar. Orange when healthy, red when critical; the next segment pulses while charging. */
@Composable
fun ArmorBar(percent: Int, charging: Boolean, modifier: Modifier = Modifier, segments: Int = 10, height: Dp = 8.dp) {
    val critical = percent < 15 && !charging
    val lit = ((percent / 100f) * segments).let { kotlin.math.ceil(it).toInt() }.coerceIn(if (percent > 0) 1 else 0, segments)
    val pulse = if (charging) {
        rememberInfiniteTransition(label = "regen").animateFloat(
            0.15f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "regenA",
        ).value
    } else 0f
    val on = if (critical) Ink.Crit else Ink.Orange
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
        repeat(segments) { i ->
            val color = when {
                i < lit -> on
                charging && i == lit -> Ink.Orange.copy(alpha = pulse)
                else -> Color.White.copy(alpha = 0.14f)
            }
            Box(Modifier.weight(1f).height(height).background(color))
        }
    }
}

/** Thin attribute roll bar. */
@Composable
fun RollBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(color))
    }
}

// ---------------------------------------------------------------- icons

@Composable
fun Glyph(icon: ImageVector, size: Dp, color: Color = Ink.White, modifier: Modifier = Modifier) {
    Image(icon, null, modifier.size(size), colorFilter = ColorFilter.tint(color, BlendMode.SrcIn))
}

/** The app's emblem: a three-segment ring around a core. */
@Composable
fun Emblem(size: Dp, color: Color = Ink.Orange, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.13f
        val r = this.size.minDimension / 2 - stroke / 2
        repeat(3) { i ->
            drawArc(
                color, startAngle = -83f + i * 120f, sweepAngle = 106f, useCenter = false,
                topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(stroke),
            )
        }
        drawCircle(color, radius = this.size.minDimension * 0.18f)
    }
}

private fun tintMatrix(c: Color): ColorMatrix {
    // Grayscale, then scale toward the target color. Used for icons without a themed layer.
    val r = c.red * 1.1f; val g = c.green * 1.1f; val b = c.blue * 1.1f
    return ColorMatrix(
        floatArrayOf(
            0.299f * r, 0.587f * r, 0.114f * r, 0f, 0f,
            0.299f * g, 0.587f * g, 0.114f * g, 0f, 0f,
            0.299f * b, 0.587f * b, 0.114f * b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/** An app's icon in the chosen style: white or orange line art from the themed-icon layer, or the original. */
@Composable
fun AppGlyph(entry: AppEntry, size: Dp, style: IconStyle, modifier: Modifier = Modifier) {
    val icons = LocalContext.current.graph.icons
    val full = style == IconStyle.ORIGINAL
    var icon by remember(entry.key, full) { mutableStateOf<AppIcon?>(icons.peek(entry, full)) }
    LaunchedEffect(entry.key, full) { if (icon == null) icon = icons.load(entry, full) }
    val i = icon
    if (i == null) {
        Spacer(modifier.size(size))
        return
    }
    val tint = if (style == IconStyle.ORANGE) Ink.Orange else Ink.White
    val filter = when {
        full -> null
        i.mono -> ColorFilter.tint(tint, BlendMode.SrcIn)
        else -> ColorFilter.colorMatrix(tintMatrix(tint))
    }
    Image(i.bitmap, entry.label, modifier.size(size), colorFilter = filter)
}

@Composable
fun Diamond(color: Color, size: Dp = 7.dp, filled: Boolean = true) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(this@Canvas.size.width / 2, 0f)
            lineTo(this@Canvas.size.width, this@Canvas.size.height / 2)
            lineTo(this@Canvas.size.width / 2, this@Canvas.size.height)
            lineTo(0f, this@Canvas.size.height / 2)
            close()
        }
        if (filled) drawPath(p, color) else drawPath(p, color, style = Stroke(1.5.dp.toPx()))
    }
}

/** Settings-style row with ‹ value › carousel. */
@Composable
fun OptionRow(
    label: String,
    value: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    danger: Boolean = false,
) {
    val fg = if (selected) Ink.OnOrange else Ink.White
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (selected) Ink.White else Color.Transparent)
            .drawBehind { drawRect(Ink.Line, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, 1f)) }
            .tap(onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, Type.body(15.sp, if (danger && !selected) Ink.Crit else fg, androidx.compose.ui.text.font.FontWeight.SemiBold), Modifier.weight(1f), maxLines = 1)
        if (value != null) {
            if (onPrev != null) Box(Modifier.padding(horizontal = 6.dp).tap(onPrev)) { Txt("‹", Type.title(16.sp, if (selected) Ink.OnOrange.copy(alpha = .6f) else Ink.Orange)) }
            Label(value, color = if (selected) Ink.OnOrange else Ink.White, size = 11.5f)
            if (onNext != null) Box(Modifier.padding(start = 6.dp).tap(onNext)) { Txt("›", Type.title(16.sp, if (selected) Ink.OnOrange.copy(alpha = .6f) else Ink.Orange)) }
        } else {
            Glyph(IsacIcons.Chevron, 14.dp, if (selected) Ink.OnOrange else Ink.Orange)
        }
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .then(if (on) Modifier.background(Ink.Orange) else Modifier.border(1.dp, Ink.White.copy(alpha = 0.22f)))
            .tap(onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Label(text, color = if (on) Ink.OnOrange else Ink.G2, size = 10.5f) }
}

/**
 * Space to keep clear at the top: the status bar when it's showing, otherwise the camera cutout,
 * so the tabs never sit under the punch-hole.
 */
@Composable
fun topInsets(): androidx.compose.foundation.layout.WindowInsets =
    androidx.compose.foundation.layout.WindowInsets.statusBars
        .union(androidx.compose.foundation.layout.WindowInsets.displayCutout)
        .only(androidx.compose.foundation.layout.WindowInsetsSides.Top)

@Composable
fun RowScope.Gap(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun ColumnScope.VGap(h: Dp) = Spacer(Modifier.height(h))
