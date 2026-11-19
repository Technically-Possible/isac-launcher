package app.isac.launcher.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.AppEntry
import app.isac.launcher.data.Badges
import app.isac.launcher.ui.components.CountBadge
import app.isac.launcher.data.Rarity
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.components.AppGlyph
import app.isac.launcher.ui.components.ArmorBar
import app.isac.launcher.ui.components.Emblem
import app.isac.launcher.ui.components.Gap
import app.isac.launcher.ui.components.Glyph
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.SectionHeader
import app.isac.launcher.ui.components.Tile
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.components.ring
import app.isac.launcher.ui.components.markerShape
import app.isac.launcher.ui.theme.Lex
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val SLOT_NAMES = listOf("PRIMARY", "SECONDARY", "SIDEARM", "GEAR 1", "GEAR 2", "GEAR 3", "GEAR 4", "GEAR 5", "GEAR 6")

@Composable
fun LoadoutPage(vm: LauncherViewModel, onOpenInventory: () -> Unit, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val b by vm.battery.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loadout by vm.loadout.collectAsStateWithLifecycle()
    val rarity by vm.rarity.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val junk by vm.junk.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    fun badge(e: AppEntry?) = e?.let { Badges.countFor(badges, it) } ?: 0
    val slots = loadout.map { k -> k?.let { byKey[it] } }
    val level by vm.level.collectAsStateWithLifecycle()

    // Fits the screen exactly: text rows take what they need, the item tiles share the rest by weight.
    Column(modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
        // agent header
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Emblem(34.dp)
            Gap(12.dp)
            Column {
                Txt(s.callsign.uppercase(), Type.title(19.sp))
                VGap(4.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(Lex.level(level), color = Ink.G2, size = 10.5f)
                    Gap(8.dp)
                    // XP towards the next level, from exploring.
                    Box(Modifier.width(72.dp).height(3.dp).background(Ink.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(vm.g.exploration.levelProgress()).height(3.dp).background(Ink.Orange))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            b.tempC?.let { t ->
                Label("CORE", color = Ink.G2, size = 9.5f)
                Gap(4.dp)
                Txt("${t.toInt()}°", Type.title(14.sp, if (b.hot) Ink.Crit else Ink.White))
                Gap(12.dp)
            }
            Glyph(IsacIcons.Battery, 15.dp, Ink.G2)
            Gap(4.dp)
            Txt("${b.percent}%", Type.title(14.sp, if (b.critical) Ink.Crit else Ink.White))
            Gap(12.dp)
            Glyph(IsacIcons.Box, 15.dp, Ink.G2)
            Gap(4.dp)
            Txt("${apps.count { it.key !in junk }}", Type.title(14.sp))
        }

        // clock
        val (t, ampm) = timeText(now, s.use24h)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Txt(t, Type.big(66.sp))
            if (ampm.isNotEmpty()) {
                Gap(6.dp)
                Txt(ampm, Type.title(16.sp, Ink.Orange), Modifier.padding(top = 8.dp))
            }
        }
        val z = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(22.dp).height(2.dp).background(Ink.Orange))
            Gap(10.dp)
            Label(z.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.UK)), color = Ink.G2, size = 12f)
        }

        // armor
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(Lex.armor, color = Ink.G2, size = 11f)
            Gap(10.dp)
            ArmorBar(b.percent, b.charging, Modifier.weight(1f), segments = 10, height = 9.dp)
            Gap(10.dp)
            Txt("${b.percent}%", Type.title(14.sp, if (b.critical) Ink.Crit else Ink.White))
        }

        SectionHeader("WEAPONS", "${slots.take(3).count { it != null }}/3")
        // Weights share out the spare height; the minimums stop tiles being crushed on shorter screens.
        WeaponTile(vm, 0, slots[0], rarity, usage.byPackage[slots[0]?.pkg]?.launches7d, s,
            Modifier.fillMaxWidth().weight(1.15f).heightIn(min = 60.dp), onOpenInventory, badge(slots[0]))
        VGap(8.dp)
        Row(Modifier.weight(0.95f).heightIn(min = 52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeaponTile(vm, 1, slots[1], rarity, null, s, Modifier.weight(1f).fillMaxHeight(), onOpenInventory, badge(slots[1]))
            WeaponTile(vm, 2, slots[2], rarity, null, s, Modifier.weight(1f).fillMaxHeight(), onOpenInventory, badge(slots[2]))
        }

        SectionHeader("GEAR", "${slots.drop(3).count { it != null }}/6")
        repeat(2) { r ->
            if (r == 1) VGap(8.dp)
            Row(Modifier.weight(1.1f).heightIn(min = 62.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { c ->
                    val i = 3 + r * 3 + c
                    GearTile(vm, i, slots[i], rarity, s, Modifier.weight(1f).fillMaxHeight(), onOpenInventory, badge(slots[i]))
                }
            }
        }

        SectionHeader("SKILLS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val torch by vm.torchOn.collectAsStateWithLifecycle()
            SkillButton(
                IsacIcons.Torch, "FLASHLIGHT",
                if (!vm.g.torch.available) "NOT AVAILABLE" else if (torch) "ACTIVE" else "READY",
                active = torch, cooldown = 0f, modifier = Modifier.weight(1f),
            ) { vm.g.torch.toggle() }
            var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
            val until = vm.pulseCooldownUntil
            LaunchedEffect(until) {
                while (System.currentTimeMillis() < until) {
                    tick = System.currentTimeMillis(); delay(100)
                }
                tick = System.currentTimeMillis()
            }
            val remaining = (until - tick).coerceAtLeast(0)
            SkillButton(
                IsacIcons.Pulse, "PULSE",
                if (remaining > 0) "SCANNING" else "HOLD TO TALK",
                active = false, cooldown = remaining / 6_000f, modifier = Modifier.weight(1f),
                onLongClick = { vm.listen() },
            ) { vm.pulse() }
        }

        VGap(10.dp)
        WorkStatusSlot(vm, maxWidgetHeight = 100.dp)
    }
}

@Composable
private fun WeaponTile(
    vm: LauncherViewModel, slot: Int, entry: AppEntry?, rarity: Map<String, Rarity>, launches: Int?,
    s: app.isac.launcher.data.Settings, modifier: Modifier, onEmpty: () -> Unit, badge: Int = 0,
) {
    if (entry == null) {
        EmptySlot(SLOT_NAMES[slot], modifier, onEmpty)
        return
    }
    Tile(
        rarity[entry.key] ?: Rarity.STANDARD, modifier,
        onClick = { vm.launch(entry) }, onLongClick = { vm.overlay = Overlay.Details(entry) },
    ) {
        CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(4.dp))
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppGlyph(entry, if (slot == 0) 34.dp else 26.dp, s.iconStyle)
            Gap(12.dp)
            Column(Modifier.weight(1f)) {
                Txt(entry.label.uppercase(), Type.title(if (slot == 0) 17.sp else 14.sp), maxLines = 1)
                VGap(3.dp)
                Label(SLOT_NAMES[slot], color = Ink.G2, size = 9.5f)
            }
            if (launches != null && launches > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Txt("$launches", Type.big(28.sp))
                    Label("USES / WK", color = Ink.G2, size = 8.5f)
                }
            }
        }
    }
}

@Composable
private fun GearTile(
    vm: LauncherViewModel, slot: Int, entry: AppEntry?, rarity: Map<String, Rarity>,
    s: app.isac.launcher.data.Settings, modifier: Modifier, onEmpty: () -> Unit, badge: Int = 0,
) {
    if (entry == null) {
        EmptySlot(SLOT_NAMES[slot], modifier, onEmpty)
        return
    }
    Tile(
        rarity[entry.key] ?: Rarity.STANDARD, modifier,
        onClick = { vm.launch(entry) }, onLongClick = { vm.overlay = Overlay.Details(entry) },
    ) {
        CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(4.dp))
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AppGlyph(entry, 28.dp, s.iconStyle)
            VGap(8.dp)
            Label(entry.label, color = Ink.G2, size = 9.5f)
        }
    }
}

@Composable
private fun EmptySlot(name: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .border(1.dp, Ink.Orange.copy(alpha = 0.3f))
            .background(Ink.Glass.copy(alpha = 0.4f))
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(IsacIcons.Plus, 14.dp, Ink.Orange.copy(alpha = 0.7f))
            VGap(4.dp)
            Label(name, color = Ink.G3, size = 9f)
        }
    }
}

@Composable
private fun SkillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String,
    active: Boolean, cooldown: Float, modifier: Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Row(
        modifier
            .background(Ink.Glass)
            .border(1.dp, if (active) Ink.Orange else Ink.Line)
            .tap(onClick, onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(if (active) Ink.Orange else Ink.Ground.copy(alpha = 0.6f), markerShape())
                .drawBehind {
                    val sw = 2.dp.toPx()
                    ring(Ink.White.copy(alpha = if (cooldown > 0) 0.2f else 0.6f), size.minDimension / 2 - sw / 2, width = sw)
                    if (cooldown > 0) {
                        drawArc(
                            Ink.Orange, -90f, 360f * (1 - cooldown), false,
                            topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Glyph(icon, 20.dp, if (active) Ink.OnOrange else if (cooldown > 0) Ink.G3 else Ink.White) }
        Gap(10.dp)
        Column {
            Txt(title, Type.title(13.sp))
            VGap(3.dp)
            Label(sub, color = if (active) Ink.Orange else Ink.G2, size = 9f)
        }
    }
}
