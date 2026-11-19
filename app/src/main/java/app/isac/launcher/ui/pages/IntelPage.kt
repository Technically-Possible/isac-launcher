package app.isac.launcher.ui.pages

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.onSizeChanged
import app.isac.launcher.data.IsacWidgetHostView
import app.isac.launcher.data.Moon
import app.isac.launcher.data.WeatherProblem
import app.isac.launcher.data.WidgetSlot
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.components.AppGlyph
import app.isac.launcher.ui.components.ArmorBar
import app.isac.launcher.ui.components.CompassStrip
import app.isac.launcher.ui.components.Diamond
import app.isac.launcher.ui.components.Emblem
import app.isac.launcher.ui.components.Gap
import app.isac.launcher.ui.components.Glyph
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.Panel
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.Lex
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone get() = ZoneId.systemDefault()

fun timeText(t: Long, use24h: Boolean): Pair<String, String> {
    val z = Instant.ofEpochMilli(t).atZone(zone)
    return if (use24h) z.format(DateTimeFormatter.ofPattern("HH:mm")) to ""
    // DIAMOND pads the hour, like a terminal readout: 03:09.
    else z.format(DateTimeFormatter.ofPattern(if (Ink.diamond) "hh:mm" else "h:mm")) to z.format(DateTimeFormatter.ofPattern("a", Locale.UK)).uppercase()
}

fun shortTime(t: Long, use24h: Boolean): String {
    val (a, b) = timeText(t, use24h)
    return if (b.isEmpty()) a else "$a ${b.lowercase()}"
}

@Composable
fun AgentStrip(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val b by vm.battery.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    if (Ink.diamond) {
        // DIAMOND: faction tag over the callsign on the left, link status over the power cells on the right.
        val online by vm.online.collectAsStateWithLifecycle()
        val stealth by vm.stealth.collectAsStateWithLifecycle()
        Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Emblem(28.dp)
            Gap(10.dp)
            Column {
                Txt(if (stealth) "B.T.S.U. // STEALTH" else "B.T.S.U. // NET-SYNC", Type.wide(8.5.sp, if (stealth) Ink.Orange else Ink.G2))
                VGap(4.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt(s.callsign.uppercase(), Type.wide(14.sp), maxLines = 1)
                    Gap(8.dp)
                    Txt(Lex.level(level), Type.mono(10.5.sp, Ink.G2))
                }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Txt(if (online) "LINK_SECURE" else "LINK_LOST", Type.mono(10.sp, if (online) Ink.Cobalt else Ink.Amber))
                VGap(5.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArmorBar(b.percent, b.charging, Modifier.width(78.dp), segments = 10, height = 8.dp)
                    Gap(6.dp)
                    Txt("${b.percent}%", Type.mono(11.sp, if (b.critical) Ink.Crit else Ink.White, FontWeight.Bold))
                }
            }
        }
        return
    }
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Emblem(18.dp)
        Gap(9.dp)
        Txt(s.callsign.uppercase(), Type.title(14.sp))
        Gap(10.dp)
        Label(Lex.level(level), color = Ink.G2, size = 10.5f)
        val stealth by vm.stealth.collectAsStateWithLifecycle()
        if (stealth) {
            Gap(8.dp)
            Box(Modifier.border(1.dp, Ink.Orange).padding(horizontal = 5.dp, vertical = 1.dp)) {
                Label("STEALTH", color = Ink.Orange, size = 9f)
            }
        }
        Spacer(Modifier.weight(1f))
        ArmorBar(b.percent, b.charging, Modifier.width(96.dp), segments = 8, height = 6.dp)
        Gap(8.dp)
        Txt("${b.percent}%", Type.title(13.sp, if (b.critical) Ink.Crit else Ink.White))
    }
}

/** What's playing, with previous, play/pause and next. Only shows while something is playing or paused. */
@Composable
private fun NowPlayingCard(vm: LauncherViewModel) {
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val n = np ?: return
    Panel(Modifier.fillMaxWidth().tap({ vm.g.media.open() }), padding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color(0xFF15171A)).border(1.dp, Ink.Line), contentAlignment = Alignment.Center) {
                val art = n.art
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Glyph(IsacIcons.Play, 16.dp, Ink.G3)
            }
            Gap(10.dp)
            Column(Modifier.weight(1f)) {
                Label("NOW PLAYING · ${n.app}", size = 9f)
                Txt(n.title, Type.body(14.sp, Ink.White, FontWeight.SemiBold), maxLines = 1)
                if (n.artist.isNotBlank()) Txt(n.artist, Type.body(11.5.sp, Ink.G2), maxLines = 1)
            }
            listOf(
                IsacIcons.Prev to { vm.g.media.previous(); Unit },
                (if (n.playing) IsacIcons.Pause else IsacIcons.Play) to { vm.g.media.playPause() },
                IsacIcons.Next to { vm.g.media.next(); Unit },
            ).forEachIndexed { i, (icon, action) ->
                Box(
                    Modifier.padding(start = 6.dp).size(38.dp)
                        .then(if (i == 1) Modifier.background(Ink.Orange) else Modifier.border(1.dp, Ink.Line))
                        .tap({ vm.g.haptics.tick(); action() }),
                    contentAlignment = Alignment.Center,
                ) { Glyph(icon, 16.dp, if (i == 1) Ink.OnOrange else Ink.White) }
            }
        }
    }
}

/** Today's three directives as compact progress chips. Tap one to see what it asks for. */
@Composable
private fun DailyChips(vm: LauncherViewModel) {
    val daily by vm.directives.collectAsStateWithLifecycle()
    if (daily.isEmpty()) return
    if (Ink.diamond) {
        // DIAMOND: finished objectives turn white; open ones show crimson progress.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            daily.forEach { d ->
                Column(
                    Modifier.weight(1f).border(1.dp, if (d.done) Ink.White.copy(alpha = 0.28f) else Ink.Line)
                        .tap({ vm.showToast(if (d.done) "Objective complete: ${d.text}." else "Objective: ${d.text}. Worth 50 XP.") })
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                ) {
                    Row {
                        Txt(d.kind.short, Type.mono(9.5.sp, if (d.done) Ink.White else Ink.G2), Modifier.weight(1f), maxLines = 1)
                        Txt("${d.progress}/${d.target}", Type.mono(9.5.sp, if (d.done) Ink.White else Ink.Orange))
                    }
                    VGap(6.dp)
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Ink.Line)) {
                        Box(Modifier.fillMaxWidth((d.progress.toFloat() / d.target).coerceIn(0f, 1f)).height(2.dp).background(if (d.done) Ink.White else Ink.Orange))
                    }
                }
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        daily.forEach { d ->
            Column(
                Modifier.weight(1f).border(1.dp, if (d.done) Ink.Orange else Ink.Line)
                    .tap({ vm.showToast(if (d.done) "Directive complete: ${d.text}." else "Directive: ${d.text}. Worth 50 XP.") })
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            ) {
                Row {
                    Label(d.kind.short, Modifier.weight(1f), color = if (d.done) Ink.Orange else Ink.White, size = 8.5f)
                    Label(if (d.done) "DONE" else "${d.progress}/${d.target}", color = if (d.done) Ink.Orange else Ink.G2, size = 8.5f)
                }
                VGap(4.dp)
                Box(Modifier.fillMaxWidth().height(2.dp).background(Ink.White.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth((d.progress.toFloat() / d.target).coerceIn(0f, 1f)).height(2.dp).background(Ink.Orange))
                }
            }
        }
    }
}

/** Compass with the Base of Operations waypoint and its distance. */
@Composable
private fun CompassRow(vm: LauncherViewModel) {
    val heading by vm.heading.collectAsStateWithLifecycle()
    val base by vm.base.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val fix = remember(now / 60_000) { vm.g.prefs.lastFix }
    var bearing: Float? = null
    var label: String? = null
    val b = base
    if (b != null && fix != null) {
        val out = FloatArray(2)
        android.location.Location.distanceBetween(fix.first, fix.second, b.first, b.second, out)
        val dist = out[0]
        bearing = (out[1] + 360f) % 360f
        label = when {
            dist < 150 -> "AT BASE"
            dist < 1000 -> "BASE ${dist.toInt()} M"
            else -> "BASE %.1f KM".format(Locale.UK, dist / 1000)
        }
    }
    CompassStrip(heading, bearing, label)
}

@Composable
fun IntelPage(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    // Fits the screen exactly: fixed-size panels take what they need, the photo rows share the rest.
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AgentStrip(vm)
        CompassRow(vm)
        CalendarPanel(vm)
        Directives(vm)
        NowPlayingCard(vm)
        Row(Modifier.fillMaxWidth().weight(1.6f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoFrame(vm, frame = 0, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Conditions has more lines than the moon, so it gets a little more of the height.
                WeatherPanel(vm, Modifier.weight(1.25f).fillMaxWidth())
                MoonPanel(vm, Modifier.weight(0.85f).fillMaxWidth())
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoFrame(vm, frame = 1, Modifier.weight(1.65f).fillMaxHeight())
            StashTile(vm, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// ------------------------------------------------------------------ calendar

@Composable
private fun CalendarPanel(vm: LauncherViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val days by vm.eventDays.collectAsStateWithLifecycle()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshCalendar() }
    if (Ink.diamond) {
        DiamondCalendar(vm, now, today, days) { if (!vm.g.calendar.hasAccess()) calPerm.launch(Manifest.permission.READ_CALENDAR) }
        return
    }
    Panel(Modifier.fillMaxWidth().tap({ if (!vm.g.calendar.hasAccess()) calPerm.launch(Manifest.permission.READ_CALENDAR) })) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(Modifier.width(118.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Txt(today.dayOfMonth.toString(), Type.big(96.sp), Modifier.padding(top = 6.dp))
                Column {
                    Txt(today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.UK).uppercase(), Type.title(14.sp))
                    VGap(2.dp)
                    Txt(today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.UK).uppercase(), Type.title(14.sp, Ink.Orange))
                }
            }
            Gap(8.dp)
            // Month grid with the time underneath, lined up with the weekday and month on the left.
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                MonthGrid(today, days, Modifier.fillMaxWidth())
                val s by vm.settings.collectAsStateWithLifecycle()
                val (t, ampm) = timeText(now, s.use24h)
                Row(Modifier.align(Alignment.End).padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Txt(t, Type.big(46.sp))
                    if (ampm.isNotEmpty()) {
                        Gap(6.dp)
                        Txt(ampm, Type.title(14.sp, Ink.Orange), Modifier.padding(bottom = 3.dp))
                    }
                }
            }
        }
        if (!vm.g.calendar.hasAccess()) {
            VGap(8.dp)
            Label("TAP TO SHOW YOUR EVENTS ON THE CALENDAR", color = Ink.G3, size = 9.5f)
        }
    }
}

/**
 * DIAMOND's calendar: the date in Michroma with the weekday and month beneath, the month grid
 * beside it, and a telemetry row along the bottom (week number, sunset, the time).
 */
@Composable
private fun DiamondCalendar(vm: LauncherViewModel, now: Long, today: LocalDate, days: Set<Int>, onTap: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Panel(Modifier.fillMaxWidth().tap(onTap), padding = 14.dp, hero = true) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(Modifier.width(128.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Txt(today.dayOfMonth.toString(), Type.day(82.sp), Modifier.padding(top = 4.dp), maxLines = 1)
                Column {
                    Txt(today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.UK).uppercase(), Type.label(14.sp, Ink.White))
                    VGap(3.dp)
                    Txt(today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.UK).uppercase(), Type.label(14.sp, Ink.Orange))
                }
            }
            Gap(10.dp)
            MonthGrid(today, days, Modifier.weight(1f))
        }
        VGap(10.dp)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
        VGap(10.dp)
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                val week = today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                Txt("WK %02d // %d".format(week, today.year), Type.mono(10.sp, Ink.G2))
                val fix = remember(today) { vm.g.prefs.lastFix }
                val sun = remember(today, fix) { fix?.let { app.isac.launcher.data.Sun.times(today, it.first, it.second) } }
                if (sun != null) {
                    VGap(3.dp)
                    Txt("SUNSET ${shortTime(sun.second, s.use24h).uppercase()}", Type.mono(10.sp, Ink.G2))
                }
                if (!vm.g.calendar.hasAccess()) {
                    VGap(3.dp)
                    Txt("TAP FOR YOUR EVENTS", Type.mono(10.sp, Ink.G3))
                }
            }
            val (t, ampm) = timeText(now, s.use24h)
            Txt(t, Type.big(54.sp))
            if (ampm.isNotEmpty()) {
                Gap(4.dp)
                Txt(ampm, Type.mono(13.sp, Ink.Orange, FontWeight.Bold), Modifier.padding(bottom = 30.dp))
            }
        }
    }
}

@Composable
private fun MonthGrid(today: LocalDate, eventDays: Set<Int>, modifier: Modifier) {
    if (Ink.diamond) {
        DiamondMonthGrid(today, eventDays, modifier)
        return
    }
    val first = today.withDayOfMonth(1)
    val lead = first.dayOfWeek.value % 7 // Sunday first, like the user's current widget
    val count = today.lengthOfMonth()
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(it, color = Ink.Orange.copy(alpha = 0.8f), size = 9.5f) }
            }
        }
        VGap(4.dp)
        val cells = lead + count
        val rows = (cells + 6) / 7
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val day = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).height(22.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..count) {
                            val isToday = day == today.dayOfMonth
                            val weekend = c == 0 || c == 6
                            if (isToday) Box(Modifier.size(width = 26.dp, height = 20.dp).background(Ink.Orange))
                            val color = when {
                                isToday -> Ink.OnOrange
                                day < today.dayOfMonth || weekend -> Ink.G3
                                else -> Ink.White
                            }
                            Txt(day.toString(), Type.body(12.5.sp, color, if (isToday) FontWeight.Bold else FontWeight.SemiBold))
                            if (day in eventDays && !isToday) {
                                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(3.dp).background(Ink.Orange))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** DIAMOND's month: monospace days, today as a white rhombus in a crimson ring, events as cobalt pips. */
@Composable
private fun DiamondMonthGrid(today: LocalDate, eventDays: Set<Int>, modifier: Modifier) {
    val first = today.withDayOfMonth(1)
    val lead = first.dayOfWeek.value % 7
    val count = today.lengthOfMonth()
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Txt(it, Type.mono(10.sp, Ink.G2)) }
            }
        }
        VGap(4.dp)
        val rows = (lead + count + 6) / 7
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { c ->
                    val day = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).height(22.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..count) {
                            val isToday = day == today.dayOfMonth
                            if (isToday) {
                                Box(Modifier.size(20.dp).border(1.5.dp, Ink.Orange, app.isac.launcher.ui.components.RhombusShape))
                                Box(Modifier.size(15.dp).background(Ink.White, app.isac.launcher.ui.components.RhombusShape))
                            }
                            Txt(
                                day.toString(),
                                Type.mono(
                                    if (isToday) 10.5.sp else 11.5.sp,
                                    when { isToday -> Ink.Ground; day < today.dayOfMonth -> Ink.G3; else -> Ink.White },
                                    if (isToday || day > today.dayOfMonth) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                            if (day in eventDays && !isToday) {
                                Box(Modifier.align(Alignment.BottomCenter).size(5.dp).background(Ink.Cobalt, app.isac.launcher.ui.components.RhombusShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Directives(vm: LauncherViewModel) {
    val alarm by vm.nextAlarm.collectAsStateWithLifecycle()
    val event by vm.nextEvent.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val daily by vm.directives.collectAsStateWithLifecycle()
    if (alarm == null && event == null && daily.isEmpty()) return
    Panel(Modifier.fillMaxWidth(), padding = 10.dp) {
        if (Ink.diamond) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(Ink.Orange, app.isac.launcher.ui.components.RhombusShape))
                Gap(8.dp)
                Label(Lex.directives)
                Gap(8.dp)
                Box(Modifier.weight(1f).height(1.dp).background(Ink.Line))
                if (daily.isNotEmpty()) {
                    Gap(8.dp)
                    Txt("${daily.count { it.done }}/${daily.size} COMPLETE", Type.mono(10.sp, Ink.G2))
                }
            }
        } else Label(Lex.directives)
        alarm?.let { a ->
            VGap(6.dp)
            DirectiveLine("Next alarm ${shortTime(a, s.use24h)}", dayWord(a), true)
        }
        event?.let { e ->
            VGap(6.dp)
            DirectiveLine(e.title, "${dayWord(e.begin)} · ${shortTime(e.begin, s.use24h)}", false)
        }
        if (alarm != null || event != null) VGap(8.dp) else VGap(6.dp)
        DailyChips(vm)
    }
}

private fun dayWord(t: Long): String {
    val d = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.UK)
    }
}

@Composable
private fun DirectiveLine(title: String, sub: String, bright: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Diamond(if (bright) Ink.Orange else Ink.G2, 8.dp, filled = bright)
        Gap(10.dp)
        Txt(title, Type.body(14.sp, if (bright) Ink.White else Ink.G2, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        Gap(8.dp)
        Txt(sub, Type.body(12.sp, Ink.G3))
    }
}

// ------------------------------------------------------------------ photos

/** A photo frame: a hosted photo widget if one is set, otherwise photos from a folder. */
@Composable
private fun PhotoFrame(vm: LauncherViewModel, frame: Int, modifier: Modifier) {
    val ids by vm.widgetIds.collectAsStateWithLifecycle()
    val slot = if (frame == 0) WidgetSlot.PHOTO1 else WidgetSlot.PHOTO2
    val widgetId = ids[slot] ?: -1
    if (widgetId != -1) {
        Box(modifier.border(1.dp, if (Ink.diamond) Ink.White.copy(alpha = 0.16f) else Ink.Orange.copy(alpha = 0.55f)).padding(1.dp)) {
            HostedWidget(vm, slot, widgetId, Modifier.fillMaxSize().then(opticFilter(vm)))
            if (Ink.diamond) OpticOverlay(frame, null)
        }
        return
    }
    FolderFrame(vm, frame, slot, modifier)
}

@Composable
private fun FolderFrame(vm: LauncherViewModel, frame: Int, slot: WidgetSlot, modifier: Modifier) {
    val hasFolder by vm.hasPhotoFolder.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setPhotoFolder(uri)
    }
    val photo = vm.g.photos.pick(frame, now, s.photoMinutes, vm.photoSkips[frame])
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(photo?.uri) {
        bitmap = photo?.let { vm.g.photos.load(it, 1200) }
    }
    Box(
        modifier
            .border(1.dp, if (Ink.diamond) Ink.White.copy(alpha = 0.16f) else Ink.Orange.copy(alpha = 0.55f))
            .background(Color(0xFF15171A))
            .tap(
                onClick = { if (photo != null) vm.skipPhoto(frame) else vm.overlay = Overlay.WidgetPicker(slot) },
                onLongClick = { vm.overlay = Overlay.WidgetPicker(slot) },
            ),
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize().then(opticFilter(vm)), contentScale = ContentScale.Crop)
        } else if (hasFolder) {
            Column(Modifier.align(Alignment.Center).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Glyph(IsacIcons.Photo, 26.dp, Ink.Orange)
                VGap(8.dp)
                Label("LOADING PHOTOS", color = Ink.G2, size = 9.5f)
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Glyph(IsacIcons.Photo, 26.dp, Ink.Orange)
                VGap(10.dp)
                Row(
                    Modifier.border(1.dp, Ink.Orange).tap({ vm.overlay = Overlay.WidgetPicker(slot) }).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(IsacIcons.Plus, 12.dp, Ink.Orange)
                    Gap(6.dp)
                    Label("PHOTO WIDGET", color = Ink.White, size = 10f)
                }
                VGap(8.dp)
                Box(Modifier.tap({ pickFolder.launch(null) }).padding(6.dp)) {
                    Label("OR CHOOSE A FOLDER", color = Ink.G2, size = 9f)
                }
            }
        }
        if (Ink.diamond) {
            OpticOverlay(frame, photo?.modified)
            return@Box
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).background(Ink.Orange))
            Gap(6.dp)
            Label("ECHO ${frame + 1}", color = Ink.White, size = 9.5f)
            Spacer(Modifier.weight(1f))
            if (photo != null) {
                val date = Instant.ofEpochMilli(photo.modified).atZone(zone).toLocalDate()
                Label(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)), color = Ink.G2, size = 9f)
            }
        }
    }
}

/**
 * DIAMOND renders photos as black-and-white optic feeds (switchable in Settings).
 * Works on hosted widgets too on Android 12 and later.
 */
@Composable
private fun opticFilter(vm: LauncherViewModel): Modifier {
    val s by vm.settings.collectAsStateWithLifecycle()
    if (!Ink.diamond || !s.opticMono || android.os.Build.VERSION.SDK_INT < 31) return Modifier
    return Modifier.graphicsLayer {
        val m = android.graphics.ColorMatrix().apply { setSaturation(0f) }
        val contrast = android.graphics.ColorMatrix(floatArrayOf(
            1.15f, 0f, 0f, 0f, -14f,
            0f, 1.15f, 0f, 0f, -14f,
            0f, 0f, 1.15f, 0f, -14f,
            0f, 0f, 0f, 1f, 0f,
        ))
        m.postConcat(contrast)
        renderEffect = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(m)).asComposeRenderEffect()
    }
}

/** DIAMOND's frame furniture: corner brackets, a crimson scan line and an OPTIC caption bar. */
@Composable
private fun BoxScope.OpticOverlay(frame: Int, modified: Long?) {
    Box(Modifier.matchParentSize().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 30.dp).drawBehind {
        val l = 12.dp.toPx()
        val w = 2.dp.toPx()
        val c = Ink.White
        drawRect(c, Offset(0f, 0f), Size(l, w)); drawRect(c, Offset(0f, 0f), Size(w, l))
        drawRect(c, Offset(size.width - l, 0f), Size(l, w)); drawRect(c, Offset(size.width - w, 0f), Size(w, l))
        drawRect(c, Offset(0f, size.height - w), Size(l, w)); drawRect(c, Offset(0f, size.height - l), Size(w, l))
        drawRect(c, Offset(size.width - l, size.height - w), Size(l, w)); drawRect(c, Offset(size.width - w, size.height - l), Size(w, l))
    })
    Box(Modifier.matchParentSize().drawBehind {
        drawRect(Ink.Orange.copy(alpha = 0.55f), Offset(0f, size.height * 0.41f), Size(size.width, 1.dp.toPx()))
    })
    Row(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(24.dp).background(Color(0xE00B0D10)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(Ink.Orange, app.isac.launcher.ui.components.RhombusShape))
        Gap(6.dp)
        Txt("OPTIC 0${frame + 1}", Type.mono(9.5.sp, Ink.White))
        Spacer(Modifier.weight(1f))
        if (modified != null) {
            val date = Instant.ofEpochMilli(modified).atZone(zone).toLocalDate()
            Txt(date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)).uppercase(), Type.mono(9.sp, Ink.G2))
        }
    }
}

// ------------------------------------------------------------------ weather

@Composable
private fun WeatherPanel(vm: LauncherViewModel, modifier: Modifier) {
    val w by vm.weather.collectAsStateWithLifecycle()
    val problem by vm.weatherProblem.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshWeather() }
    Panel(
        modifier.tap({
            if (problem == WeatherProblem.NEED_LOCATION) locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            else vm.refreshWeather()
        }),
        padding = 10.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("CONDITIONS")
            Spacer(Modifier.weight(1f))
            w?.place?.takeIf { it.isNotBlank() }?.let {
                Label(it, Modifier.padding(start = 8.dp).weight(1f, fill = false), color = Ink.G3, size = 9f)
            }
        }
        val cur = w
        if (cur == null) {
            Spacer(Modifier.weight(1f))
            Txt(
                when (problem) {
                    WeatherProblem.NEED_LOCATION -> "Tap to allow approximate location, or set a town in Settings."
                    WeatherProblem.TOWN_NOT_FOUND -> "Couldn't find that town. Check the name in Settings."
                    WeatherProblem.NO_LOCATION_FIX -> "No location yet. Tap to try again."
                    WeatherProblem.OFFLINE -> "No connection. Tap to try again."
                    null -> "Loading…"
                },
                Type.body(12.sp, Ink.G2),
            )
            Spacer(Modifier.weight(1f))
        } else {
            // Packed towards the bottom so the pressure line always fits.
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Ink.diamond) {
                    // Michroma's degree sign reads like a letter o, so the sign comes from the label face.
                    Row(verticalAlignment = Alignment.Top) {
                        Txt("${cur.tempC}", Type.wide(28.sp))
                        Txt("°", Type.title(22.sp))
                    }
                } else Txt("${cur.tempC}°", Type.big(38.sp))
                Spacer(Modifier.weight(1f))
                Glyph(IsacIcons.weather(cur.code, cur.isDay), 30.dp, Ink.White)
            }
            VGap(4.dp)
            Txt(cur.description, Type.body(13.sp, Ink.White, FontWeight.SemiBold), maxLines = 1)
            Label("H ${cur.highC}° · L ${cur.lowC}°", color = Ink.G2, size = 9.5f)
            val hPa by vm.pressure.collectAsStateWithLifecycle()
            hPa?.let { p ->
                val trend = remember(p.toInt()) { vm.pressureTrend() }
                val word = when {
                    trend == null -> ""
                    trend <= -1f -> " · FALLING"
                    trend >= 1f -> " · RISING"
                    else -> " · STEADY"
                }
                Label("${p.toInt()} HPA$word", color = if (trend != null && trend <= -3f) Ink.Orange else Ink.G2, size = 9.5f)
            }
        }
    }
}

// ------------------------------------------------------------------ moon

@Composable
private fun MoonPanel(vm: LauncherViewModel, modifier: Modifier) {
    val now by vm.now.collectAsStateWithLifecycle()
    val m = remember(now / 3_600_000) { Moon.at(now) }
    Panel(modifier, padding = 10.dp) {
        Label(if (Ink.diamond) "LUNAR" else "MOON")
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(m.name, Type.body(13.sp, Ink.White, FontWeight.SemiBold))
                Label("${(m.illumination * 100).toInt()}% LIT", color = Ink.G2, size = 9.5f)
            }
            MoonDisc(m, Modifier.size(46.dp))
        }
        Spacer(Modifier.weight(1f))
        val newSoon = m.nextNew < m.nextFull
        val next = if (newSoon) m.nextNew else m.nextFull
        val d = Instant.ofEpochMilli(next).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM", Locale.UK))
        Label(if (newSoon) "NEW MOON · $d" else "FULL MOON · $d", color = Ink.Orange.copy(alpha = 0.85f), size = 9.5f)
    }
}

/** Draws the lit part of the moon. Northern hemisphere: waxing is lit on the right. */
@Composable
private fun MoonDisc(m: Moon.Phase, modifier: Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val r = size.minDimension / 2
        val lit = Color(0xFFE9E5DD)
        val dark = Color(0xFF1C1F23)
        // Under DIAMOND the moon is drawn inside a rhombus, like every other marker.
        val circle = if (Ink.diamond) app.isac.launcher.ui.components.rhombusPath(center, r)
        else androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
        drawPath(circle, dark)
        clipPath(circle) {
            val k = m.illumination.toFloat()
            val rightLit = m.waxing
            drawArc(lit, if (rightLit) -90f else 90f, 180f, true, topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r))
            val rx = r * kotlin.math.abs(1 - 2 * k)
            val ovalColor = if (k < 0.5f) dark else lit
            drawOval(ovalColor, topLeft = Offset(center.x - rx, center.y - r), size = Size(2 * rx, 2 * r))
            // a few soft maria
            drawCircle(Color.Black.copy(alpha = 0.10f), r * 0.22f, Offset(center.x - r * 0.25f, center.y - r * 0.2f))
            drawCircle(Color.Black.copy(alpha = 0.08f), r * 0.16f, Offset(center.x + r * 0.2f, center.y + r * 0.3f))
        }
    }
}

// ------------------------------------------------------------------ stash + widget slot

@Composable
private fun StashTile(vm: LauncherViewModel, modifier: Modifier) {
    val stash by vm.stash.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val entries = stash.mapNotNull { k -> apps.firstOrNull { it.key == k } }
    val badges by vm.badges.collectAsStateWithLifecycle()
    val total = entries.sumOf { app.isac.launcher.data.Badges.countFor(badges, it) }
    Box(modifier) {
    Panel(Modifier.fillMaxSize().tap({ vm.overlay = Overlay.Stash }), padding = 10.dp) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) { r ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(2) { c ->
                        val e = entries.getOrNull(r * 2 + c)
                        Box(
                            Modifier.weight(1f).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)).border(1.dp, Ink.Line),
                            contentAlignment = Alignment.Center,
                        ) { if (e != null) AppGlyph(e, 20.dp, s.iconStyle) }
                    }
                }
            }
        }
        VGap(8.dp)
        Txt(s.stashName.uppercase(), if (Ink.diamond) Type.wide(11.sp) else Type.title(13.sp), maxLines = 1)
        if (Ink.diamond) Txt("%02d ITEMS".format(entries.size), Type.mono(9.5.sp, Ink.G2))
        else Label("${entries.size} ITEMS", color = Ink.G2, size = 9f)
    }
    // Total unread across the stash, on the folder's corner.
    app.isac.launcher.ui.components.CountBadge(total, Modifier.align(Alignment.TopEnd).padding(5.dp))
    }
}

/** The work status widget slot (e.g. Slack's custom status widget). Shown on the Loadout page. */
@Composable
internal fun WorkStatusSlot(vm: LauncherViewModel, modifier: Modifier = Modifier, maxWidgetHeight: androidx.compose.ui.unit.Dp = 200.dp) {
    val ids by vm.widgetIds.collectAsStateWithLifecycle()
    val id = ids[WidgetSlot.WORK] ?: -1
    Panel(modifier.fillMaxWidth(), padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("WORK STATUS")
            Spacer(Modifier.weight(1f))
            if (id == -1) {
                Label("WIDGET SLOT", color = Ink.G3, size = 9f)
            } else {
                Box(Modifier.tap({ vm.overlay = Overlay.WidgetPicker(WidgetSlot.WORK) }).padding(start = 12.dp, top = 2.dp, bottom = 2.dp)) {
                    Label("CHANGE ›", color = Ink.Orange, size = 9.5f)
                }
            }
        }
        VGap(8.dp)
        if (id == -1) {
            Row(
                Modifier.fillMaxWidth().height(46.dp).border(1.dp, Ink.Orange.copy(alpha = 0.4f)).tap({ vm.overlay = Overlay.WidgetPicker(WidgetSlot.WORK) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Glyph(IsacIcons.Plus, 16.dp, Ink.Orange)
                Gap(8.dp)
                Label(if (Ink.diamond) "MOUNT STATUS WIDGET" else "ADD YOUR STATUS WIDGET", color = Ink.White, size = 11f)
            }
        } else {
            val info = remember(id) { vm.g.widgets.info(id) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            val minH = info?.let { with(density) { it.minHeight.toDp() } }?.coerceIn(56.dp, maxWidgetHeight) ?: 56.dp
            HostedWidget(vm, WidgetSlot.WORK, id, Modifier.fillMaxWidth().height(minH))
        }
    }
}

/** A regular Android widget inside ISAC. Long-press it to change or remove it. */
@Composable
internal fun HostedWidget(vm: LauncherViewModel, slot: WidgetSlot, id: Int, modifier: Modifier) {
    val info = remember(id) { vm.g.widgets.info(id) }
    if (info == null) {
        Box(modifier.tap({ vm.overlay = Overlay.WidgetPicker(slot) }).padding(10.dp), contentAlignment = Alignment.Center) {
            Txt("This widget is no longer available. Tap to replace it.", Type.body(12.sp, Ink.G2))
        }
        return
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var sizeDp by remember { mutableStateOf(androidx.compose.ui.unit.DpSize.Zero) }
    AndroidView(
        factory = { ctx ->
            vm.g.widgets.host.createView(ctx, id, info).apply {
                setPadding(0, 0, 0, 0)
                (this as? IsacWidgetHostView)?.onLongPress = { vm.overlay = Overlay.WidgetPicker(slot) }
            }
        },
        update = { view -> vm.g.widgets.updateSize(view, sizeDp.width.value, sizeDp.height.value) },
        modifier = modifier.onSizeChanged { px ->
            sizeDp = with(density) { androidx.compose.ui.unit.DpSize(px.width.toDp(), px.height.toDp()) }
        },
    )
}
