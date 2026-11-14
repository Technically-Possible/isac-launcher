package app.isac.launcher.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.Badges
import app.isac.launcher.data.Rarity
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.components.AppGlyph
import app.isac.launcher.ui.components.ArmorBar
import app.isac.launcher.ui.components.CompassStrip
import app.isac.launcher.ui.components.CountBadge
import app.isac.launcher.ui.components.Diamond
import app.isac.launcher.ui.components.Emblem
import app.isac.launcher.ui.components.Gap
import app.isac.launcher.ui.components.Glyph
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.Tile
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)

/**
 * The Field HUD: the city in full view with only what you need on top, laid out like the game's HUD.
 * Objectives top-left, status top-right, quick items in the middle, skills and armor bottom-left,
 * and the clock bottom-right where the ammo counter would be.
 */
@Composable
fun HudPage(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val b by vm.battery.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val alarm by vm.nextAlarm.collectAsStateWithLifecycle()
    val event by vm.nextEvent.collectAsStateWithLifecycle()
    val daily by vm.directives.collectAsStateWithLifecycle()
    val heading by vm.heading.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loadout by vm.loadout.collectAsStateWithLifecycle()
    val rarity by vm.rarity.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val torch by vm.torchOn.collectAsStateWithLifecycle()
    val stealth by vm.stealth.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val quick = loadout.mapNotNull { k -> k?.let { byKey[it] } }.take(5)

    Box(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            VGap(6.dp)
            CompassStrip(heading, null, null)
            VGap(10.dp)
            Row(verticalAlignment = Alignment.Top) {
                // Objectives
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(Ink.Orange))
                        Gap(8.dp)
                        Label("DIRECTIVES", size = 11f)
                    }
                    alarm?.let { Objective("Next alarm ${shortTime(it, s.use24h)}", null, true) }
                    event?.let { Objective(it.title, shortTime(it.begin, s.use24h), false) }
                    daily.forEach { d -> Objective(d.text, if (d.done) "DONE" else "${d.progress}/${d.target}", !d.done, done = d.done) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Emblem(16.dp)
                        Gap(6.dp)
                        Label(if (stealth) "STEALTH" else "ONLINE", color = if (stealth) Ink.Orange else Ink.G2, size = 10f)
                    }
                    weather?.let {
                        VGap(6.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(IsacIcons.weather(it.code, it.isDay), 16.dp, Ink.White)
                            Gap(5.dp)
                            Txt("${it.tempC}°", Type.title(14.sp).copy(shadow = shadow))
                        }
                    }
                }
            }
        }

        // Quick items
        Row(Modifier.align(Alignment.Center).padding(top = 150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { e ->
                Tile(rarity[e.key] ?: Rarity.STANDARD, Modifier.size(56.dp), onClick = { vm.launch(e) }, onLongClick = { vm.overlay = Overlay.Details(e) }) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppGlyph(e, 28.dp, s.iconStyle) }
                    CountBadge(Badges.countFor(badges, e), Modifier.align(Alignment.TopEnd).padding(2.dp))
                }
            }
        }

        // Skills and armor
        Column(Modifier.align(Alignment.BottomStart).padding(bottom = 18.dp).width(150.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkillDot(IsacIcons.Torch, torch) { vm.g.torch.toggle() }
                SkillDot(IsacIcons.Pulse, false, onLong = { vm.listen() }) { vm.pulse() }
            }
            VGap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.border(1.dp, Ink.White.copy(alpha = 0.6f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Txt("$level", Type.title(11.sp))
                }
                Gap(7.dp)
                ArmorBar(b.percent, b.charging, Modifier.weight(1f), segments = 8, height = 8.dp)
            }
            VGap(5.dp)
            Label("ARMOR ${b.percent}%", color = if (b.critical) Ink.Crit else Ink.G2, size = 10f)
        }

        // Clock, where the ammo counter would be
        Column(Modifier.align(Alignment.BottomEnd).padding(bottom = 14.dp), horizontalAlignment = Alignment.End) {
            val z = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
            Label(z.format(DateTimeFormatter.ofPattern("EEEE", Locale.UK)), color = Ink.G2, size = 11f)
            val (t, ampm) = timeText(now, s.use24h)
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(t, Type.big(64.sp).copy(shadow = shadow))
                Gap(6.dp)
                Column(Modifier.padding(bottom = 6.dp)) {
                    if (ampm.isNotEmpty()) Txt(ampm, Type.title(17.sp, Ink.Orange))
                    Label(z.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)), color = Ink.G2, size = 10f)
                }
            }
        }
    }
}

@Composable
private fun Objective(title: String, sub: String?, bright: Boolean, done: Boolean = false) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Diamond(if (done) Ink.Orange else Ink.White, 8.dp, filled = done)
        Gap(9.dp)
        Txt(title, Type.body(14.sp, if (bright) Ink.White else Ink.G2, FontWeight.SemiBold).copy(shadow = shadow), Modifier.weight(1f, fill = false), maxLines = 1)
        if (sub != null) {
            Gap(8.dp)
            Label(sub, color = if (done) Ink.Orange else Ink.G2, size = 10f)
        }
    }
}

@Composable
private fun SkillDot(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).background(if (active) Ink.Orange else Color(0x99000000), CircleShape)
            .border(1.5.dp, Ink.White.copy(alpha = 0.6f), CircleShape).tap(onClick, onLong),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, 20.dp, if (active) Ink.OnOrange else Ink.White) }
}
