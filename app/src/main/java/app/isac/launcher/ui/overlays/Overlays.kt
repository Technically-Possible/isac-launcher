package app.isac.launcher.ui.overlays

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.AppEntry
import app.isac.launcher.data.Backdrop
import app.isac.launcher.data.LOADOUT_SLOTS
import app.isac.launcher.data.Rarity
import app.isac.launcher.data.WidgetSlot
import app.isac.launcher.ui.InputKind
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.Toast
import app.isac.launcher.ui.components.AppGlyph
import app.isac.launcher.ui.components.CityBackdrop
import app.isac.launcher.ui.components.Diamond
import app.isac.launcher.ui.components.Emblem
import app.isac.launcher.ui.components.Gap
import app.isac.launcher.ui.components.Glyph
import app.isac.launcher.ui.components.KeyChip
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.Panel
import app.isac.launcher.ui.components.RollBar
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.pages.InventoryItem
import app.isac.launcher.ui.pages.SLOT_NAMES
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dimmed full-screen layer; tapping outside the content closes it. */
@Composable
fun Scrim(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB3050608)).tap(onDismiss), content = content)
}

// ------------------------------------------------------------------ item details

private fun talent(r: Rarity, days: Int, hasAccess: Boolean): Pair<String, String> = when (r) {
    Rarity.EXOTIC -> "Top of the Loadout" to "One of your three most-used apps this week. It stays Exotic while it's in your top three."
    Rarity.HIGH_END -> "Daily Driver" to "Used on $days of the last 7 days."
    Rarity.SUPERIOR -> "In Rotation" to "Used in the last 7 days."
    Rarity.SPECIALIZED -> "Field Kit" to "Used in the last 30 days."
    Rarity.STANDARD -> if (hasAccess) "Standard Issue" to "Opened in the last 90 days."
        else "Standard Issue" to "Turn on Usage Access to see how often you use this."
    Rarity.WORN -> "Gathering Dust" to "Not opened in 90 days. A good candidate to dismantle."
    Rarity.GEAR_SET -> "Work Set" to "Part of your work profile."
}

private fun duration(ms: Long): String {
    val m = ms / 60_000
    return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
}

@Composable
fun ItemDetails(vm: LauncherViewModel, entry: AppEntry) {
    val rarityMap by vm.rarity.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val stash by vm.stash.collectAsStateWithLifecycle()
    val junk by vm.junk.collectAsStateWithLifecycle()
    val loadout by vm.loadout.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val r = rarityMap[entry.key] ?: Rarity.STANDARD
    val rc = Ink.rarity(r)
    val u = usage.byPackage[entry.pkg]
    var storage by remember(entry.key) { mutableStateOf<Long?>(null) }
    LaunchedEffect(entry.key) { storage = vm.g.usage.storageBytes(entry) }
    val close = { vm.overlay = Overlay.None }

    Scrim(close) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .tap({}) // swallow taps on the card
                .background(Ink.GlassDeep),
        ) {
            // header
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(rc.copy(alpha = 0.5f), Color(0xFF121314)), start = Offset.Zero, end = Offset(900f, 700f)))
                    .padding(top = 3.dp)
                    .background(Color.Transparent),
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(rc).align(Alignment.TopStart))
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Label(r.label, color = rc, size = 11f)
                        if (entry.category.label.isNotEmpty()) Label("  ·  ${entry.category.label}", color = Ink.G2, size = 11f)
                    }
                    VGap(6.dp)
                    Txt(entry.label.uppercase(), Type.title(28.sp), maxLines = 2)
                    VGap(12.dp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Txt(u?.foreground7d?.takeIf { it > 0 }?.let(::duration) ?: "—", Type.big(36.sp))
                        Gap(10.dp)
                        Column(Modifier.padding(bottom = 4.dp)) {
                            Label("SCREEN TIME", color = Ink.G2, size = 9f)
                            Label("LAST 7 DAYS", color = Ink.G2, size = 9f)
                        }
                    }
                }
                AppGlyph(entry, 48.dp, s.iconStyle, Modifier.align(Alignment.TopEnd).padding(18.dp))
            }
            // attributes
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Label("ATTRIBUTES", color = Ink.G3, size = 9.5f)
                VGap(8.dp)
                Attribute(Ink.AttrRed, "Launches, 7 days", u?.launches7d?.toString() ?: "—", u?.let { it.launches7d / usage.maxLaunches.toFloat() })
                VGap(10.dp)
                Attribute(Ink.AttrBlue, "Storage", storage?.let { "%.0f MB".format(it / 1_048_576.0) } ?: "—", storage?.let { (it / 2_147_483_648.0).toFloat() })
                VGap(10.dp)
                Attribute(Ink.AttrYellow, "Installed",
                    Instant.ofEpochMilli(entry.firstInstall).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)), null)
                VGap(12.dp)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
                VGap(10.dp)
                val (tName, tDesc) = talent(r, u?.daysUsed7d ?: 0, usage.hasAccess)
                Label("TALENT", color = Ink.Orange, size = 9.5f)
                VGap(4.dp)
                Txt(tName, Type.title(14.sp))
                VGap(2.dp)
                Txt(tDesc, Type.body(12.5.sp, Ink.G2))
            }
            // actions
            Column(Modifier.fillMaxWidth().background(Color(0xF0090A0B))) {
                val equipped = entry.key in loadout
                // The app's own shortcuts, like "New chat". Only the default home app gets these.
                val shortcuts = remember(entry.key) { vm.g.apps.shortcuts(entry) }
                shortcuts.forEach { sc ->
                    Action("›", (sc.shortLabel ?: sc.longLabel ?: "Shortcut").toString().uppercase(), "Shortcut") { vm.g.apps.startShortcut(sc); close() }
                }
                Action("+", if (equipped) "RE-EQUIP" else "EQUIP", "Put in a loadout slot") { vm.overlay = Overlay.SlotPicker(entry) }
                Action("▤", if (entry.key in stash) "REMOVE FROM ${s.stashName.uppercase()}" else "ADD TO ${s.stashName.uppercase()}", "App folder") {
                    vm.g.prefs.toggleStash(entry.key)
                }
                Action("−", if (entry.key in junk) "UNMARK JUNK" else "MARK AS JUNK", if (entry.key in junk) "Show in inventory" else "Hide from inventory") {
                    vm.g.prefs.toggleJunk(entry.key); close()
                }
                Action("i", "APP INFO", "Android settings") { vm.g.apps.openAppInfo(entry); close() }
                Action("×", "DISMANTLE", "Uninstall", danger = true) { vm.g.haptics.heavy(); vm.g.apps.uninstall(entry); close() }
            }
        }
    }
}

@Composable
private fun Attribute(color: Color, name: String, value: String, fraction: Float?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Diamond(color, 8.dp)
            Gap(10.dp)
            Txt(name, Type.body(13.5.sp), Modifier.weight(1f))
            Txt(value, Type.body(13.5.sp, weight = FontWeight.SemiBold))
        }
        if (fraction != null) {
            VGap(5.dp)
            Box(Modifier.padding(start = 18.dp)) { RollBar(fraction, color) }
        }
    }
}

@Composable
private fun Action(key: String, title: String, hint: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).tap(onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyChip(text = key)
        Gap(12.dp)
        Label(title, color = if (danger) Ink.Crit else Ink.White, size = 12f)
        Spacer(Modifier.weight(1f))
        Txt(hint, Type.body(12.sp, Ink.G2))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
}

/** A pinned shortcut (for example a web app added from the browser): open it or remove it. */
@Composable
fun ShortcutPanel(vm: LauncherViewModel, sc: android.content.pm.ShortcutInfo) {
    val close = { vm.overlay = Overlay.None }
    Scrim(close) {
        Panel(Modifier.align(Alignment.Center).padding(horizontal = 18.dp).fillMaxWidth().tap({}), padding = 0.dp, fill = Ink.GlassDeep) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                app.isac.launcher.ui.pages.ShortcutGlyph(vm, sc, 44.dp)
                Gap(14.dp)
                Column {
                    Label("SHORTCUT")
                    VGap(4.dp)
                    Txt((sc.shortLabel ?: "Shortcut").toString().uppercase(), Type.title(22.sp), maxLines = 2)
                }
            }
            Action("›", "OPEN", "Launch") { vm.g.apps.startShortcut(sc); close() }
            Action("×", "REMOVE", "Take it out of your inventory", danger = true) { vm.g.haptics.heavy(); vm.g.apps.unpin(sc); close() }
        }
    }
}

// ------------------------------------------------------------------ slot picker

@Composable
fun SlotPicker(vm: LauncherViewModel, entry: AppEntry) {
    val loadout by vm.loadout.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val byKey = apps.associateBy { it.key }
    val close = { vm.overlay = Overlay.None }
    Scrim(close) {
        Panel(Modifier.align(Alignment.Center).padding(horizontal = 18.dp).fillMaxWidth().tap({}), padding = 0.dp, fill = Ink.GlassDeep) {
            Column(Modifier.padding(16.dp)) {
                Label("EQUIP")
                VGap(4.dp)
                Txt(entry.label.uppercase(), Type.title(22.sp))
                VGap(2.dp)
                Txt("Choose a slot. Whatever's there now goes back to your inventory.", Type.body(12.5.sp, Ink.G2))
            }
            repeat(LOADOUT_SLOTS) { i ->
                if (i == 3) Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Orange.copy(alpha = 0.4f)))
                val current = loadout[i]?.let { byKey[it] }
                Row(
                    Modifier.fillMaxWidth().height(48.dp)
                        .background(if (current?.key == entry.key) Ink.Orange.copy(alpha = 0.15f) else Color.Transparent)
                        .tap({ vm.g.haptics.confirm(); vm.g.prefs.equip(i, entry.key); close() })
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(SLOT_NAMES[i], color = if (i < 3) Ink.Orange else Ink.G2, size = 11f, modifier = Modifier.width(96.dp))
                    if (current != null) {
                        AppGlyph(current, 18.dp, s.iconStyle)
                        Gap(8.dp)
                        Txt(current.label, Type.body(14.sp), maxLines = 1)
                    } else {
                        Txt("Empty", Type.body(14.sp, Ink.G3))
                    }
                }
            }
            if (entry.key in loadout) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).tap({
                        vm.g.prefs.setLoadout(loadout.map { if (it == entry.key) null else it }); close()
                    }).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KeyChip(text = "×")
                    Gap(12.dp)
                    Label("UNEQUIP", color = Ink.Crit, size = 12f)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ stash

@Composable
fun StashPanel(vm: LauncherViewModel) {
    val stash by vm.stash.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val rarity by vm.rarity.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val entries = stash.mapNotNull { k -> apps.firstOrNull { it.key == k } }
        .sortedByDescending { usage.byPackage[it.pkg]?.foreground7d ?: 0L }
    Scrim({ vm.overlay = Overlay.None }) {
        Panel(Modifier.align(Alignment.Center).padding(horizontal = 16.dp).fillMaxWidth().tap({}), padding = 16.dp, fill = Ink.GlassDeep) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(s.stashName.uppercase(), Type.title(24.sp), Modifier.weight(1f), maxLines = 1)
                Row(Modifier.tap({ vm.overlay = Overlay.TextInput(InputKind.STASH_NAME) }), verticalAlignment = Alignment.CenterVertically) {
                    KeyChip(text = "A")
                    Gap(6.dp)
                    Label("RENAME", color = Ink.White, size = 10.5f)
                }
            }
            VGap(4.dp)
            Label("${entries.size} ITEMS · MOST USED FIRST", color = Ink.G3, size = 10f)
            VGap(12.dp)
            if (entries.isEmpty()) {
                Txt("Your stash is empty. Long-press any item in Inventory and choose Add to ${s.stashName.lowercase().replaceFirstChar { it.uppercase() }}.", Type.body(13.5.sp, Ink.G2))
            } else {
                LazyVerticalGrid(
                    GridCells.Fixed(4), Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries, key = { it.key }) { e ->
                        InventoryItem(e, rarity[e.key] ?: Rarity.STANDARD, s.iconStyle, isNew = false,
                            onClick = { vm.launch(e) }, onLongClick = { vm.overlay = Overlay.Details(e) },
                            badge = app.isac.launcher.data.Badges.countFor(badges, e))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ text input

@Composable
fun TextInputPanel(vm: LauncherViewModel, kind: InputKind) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val initial = when (kind) {
        InputKind.CALLSIGN -> s.callsign
        InputKind.STASH_NAME -> s.stashName
        InputKind.WEATHER_TOWN -> s.weatherTown
    }
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = {
        val v = text.trim()
        vm.g.prefs.update {
            when (kind) {
                InputKind.CALLSIGN -> it.copy(callsign = v.ifEmpty { "AGENT" })
                InputKind.STASH_NAME -> it.copy(stashName = v.ifEmpty { "STASH" })
                InputKind.WEATHER_TOWN -> it.copy(weatherTown = v)
            }
        }
        if (kind == InputKind.WEATHER_TOWN) vm.refreshWeather()
        vm.overlay = if (kind == InputKind.STASH_NAME) Overlay.Stash else Overlay.Settings
    }
    Scrim({ vm.overlay = Overlay.Settings }) {
        Panel(Modifier.align(Alignment.Center).imePadding().padding(horizontal = 18.dp).fillMaxWidth().tap({}), padding = 16.dp, fill = Ink.GlassDeep) {
            Label(kind.title)
            VGap(10.dp)
            Box(Modifier.fillMaxWidth().height(46.dp).border(1.dp, Ink.Orange).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Txt(kind.hint, Type.body(15.sp, Ink.G3))
                BasicTextField(
                    text, { text = it.take(32) }, Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true, textStyle = Type.body(16.sp, weight = FontWeight.SemiBold), cursorBrush = SolidColor(Ink.Orange),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
            VGap(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.tap({ vm.overlay = Overlay.Settings }), verticalAlignment = Alignment.CenterVertically) {
                    KeyChip(text = "×"); Gap(7.dp); Label("CANCEL", color = Ink.White, size = 11f)
                }
                Row(Modifier.tap(save), verticalAlignment = Alignment.CenterVertically) {
                    KeyChip(text = "✓"); Gap(7.dp); Label("CONFIRM", color = Ink.White, size = 11f)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ widget picker

@Composable
fun WidgetPicker(vm: LauncherViewModel, slot: WidgetSlot, onPick: (WidgetSlot, AppWidgetProviderInfo) -> Unit) {
    val providers = remember(slot) { vm.g.widgets.providers(slot) }
    val pm = androidx.compose.ui.platform.LocalContext.current.packageManager
    val ids by vm.widgetIds.collectAsStateWithLifecycle()
    val current = ids[slot] ?: -1
    val pickFolder = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            vm.g.widgets.remove(slot)
            vm.setPhotoFolder(uri)
            vm.overlay = Overlay.None
        }
    }
    Scrim({ vm.overlay = Overlay.None }) {
        Panel(Modifier.align(Alignment.Center).padding(horizontal = 16.dp, vertical = 40.dp).fillMaxWidth().tap({}), padding = 0.dp, fill = Ink.GlassDeep) {
            Column(Modifier.padding(16.dp)) {
                Label("WIDGET SLOT · ${slot.title}")
                VGap(4.dp)
                Txt("CHOOSE A WIDGET", Type.title(22.sp))
                VGap(4.dp)
                Txt(
                    if (slot.isPhoto) "Pick your photo widget. If it asks you to set it up, choose its option to import from an existing widget, and your photos copy straight across."
                    else "Pick your Slack status widget. Android may ask you to allow ISAC to add widgets once.",
                    Type.body(12.5.sp, Ink.G2),
                )
            }
            if (current != -1) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp).tap({ vm.g.widgets.remove(slot); vm.overlay = Overlay.None }).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { KeyChip(text = "×"); Gap(12.dp); Label("REMOVE CURRENT WIDGET", color = Ink.Crit, size = 11.5f) }
            }
            if (slot.isPhoto) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp).tap({ pickFolder.launch(null) }).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { KeyChip(text = "▤"); Gap(12.dp); Label("USE A PHOTO FOLDER INSTEAD", color = Ink.White, size = 11.5f) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Orange.copy(alpha = 0.4f)))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(providers, key = { it.provider.flattenToString() + it.profile.hashCode() }) { p ->
                    val suggested = vm.g.widgets.isSuggested(slot, p)
                    Row(
                        Modifier.fillMaxWidth().height(56.dp)
                            .background(if (suggested) Ink.Orange.copy(alpha = 0.08f) else Color.Transparent)
                            .tap({ onPick(slot, p) }).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt(p.loadLabel(pm), Type.body(14.5.sp, weight = FontWeight.SemiBold), maxLines = 1)
                            Label(vm.g.widgets.appLabel(p), color = Ink.G3, size = 9.5f)
                        }
                        if (suggested) {
                            Box(Modifier.background(Ink.Orange).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                Label("SUGGESTED", color = Ink.OnOrange, size = 8.5f)
                            }
                            Gap(10.dp)
                        }
                        Glyph(IsacIcons.Chevron, 14.dp, Ink.Orange)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ loading screen

@Composable
fun LoadingScreen(vm: LauncherViewModel, arrival: Boolean = false, onDone: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val tip = remember { TIPS.random() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(if (arrival) 2600 else 1600); onDone() }
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "spinA",
    )
    Box(Modifier.fillMaxSize().tap(onDone)) {
        CityBackdrop(Backdrop.CITY_BLURRED)
        Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        Row(Modifier.padding(start = 28.dp, top = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Emblem(22.dp); Gap(10.dp); Label("SHD ISAC", color = Ink.White, size = 13f)
        }
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 28.dp)) {
            Label(if (arrival) "ARRIVED" else "DESTINATION", color = if (arrival) Ink.Orange else Ink.G2, size = 11f)
            VGap(10.dp)
            Txt("BASE OF\nOPERATIONS", Type.title(42.sp))
            VGap(16.dp)
            Box(Modifier.width(56.dp).height(3.dp).background(Ink.Orange))
            VGap(12.dp)
            val agent = "Agent ${s.callsign.lowercase().replaceFirstChar { it.uppercase() }}"
            Txt(if (arrival) "Secure. Welcome back, $agent." else "$agent · Level $level", Type.body(15.sp, Ink.G2))
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, end = 28.dp, bottom = 120.dp)) {
            Box(Modifier.width(3.dp).height(58.dp).background(Ink.Orange))
            Gap(12.dp)
            Column {
                Label("TIP")
                VGap(4.dp)
                Txt(tip, Type.body(15.sp))
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(22.dp)) {
                rotate(spin) {
                    val sw = 3.dp.toPx()
                    repeat(3) { i ->
                        drawArc(Ink.Orange, i * 120f, 80f, false, topLeft = Offset(sw / 2, sw / 2),
                            size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
                    }
                }
            }
            Gap(10.dp)
            Label("LOADING", color = Ink.G2, size = 11f)
        }
    }
}

private val TIPS = listOf(
    "Long-press any item to equip it, mark it as junk, or dismantle it.",
    "Pulse has ISAC read out the time, your power cell and your next alarm.",
    "Tap a photo frame to skip to the next photo. Long-press it to change the folder.",
    "Items you haven't opened in 90 days turn Worn. They're good candidates to dismantle.",
    "Swipe down on the tabs to open your notifications.",
    "Press Home on any page to jump back to Intel.",
    "Every new city you visit unlocks on the map and is worth 150 XP.",
    "Start an Expedition from the map before a trip to draw your whole route.",
    "Hold Pulse and say “take me home” to plot a route to the Base of Operations.",
    "Tap any device in a Pulse scan to track it with the locator.",
)

// ------------------------------------------------------------------ pop-ups

@Composable
fun ToastLayer(toast: Toast?, modifier: Modifier = Modifier) {
    if (toast == null) return
    toast.banner?.let { b ->
        // Centre-screen banner: AREA UNLOCKED, LEVEL UP, WELCOME BACK.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0x00000000), Color(0xE6080909), Color(0xE6080909), Color(0x00000000))))
                    .padding(vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label(b.kicker, size = 12f)
                VGap(6.dp)
                Txt(b.title, Type.title(32.sp).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2)
                VGap(10.dp)
                Box(Modifier.width(56.dp).height(3.dp).background(Ink.Orange))
                VGap(10.dp)
                Txt(b.sub, Type.body(14.sp, Ink.G2))
            }
        }
        return
    }
    Box(modifier.fillMaxWidth()) {
        val item = toast.newItem
        if (item != null) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp).width(260.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xF00A0A0B), Color(0xC00A0A0B))))
                    .padding(start = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(64.dp).background(Ink.Orange))
                Gap(12.dp)
                Column(Modifier.padding(vertical = 10.dp)) {
                    Box(Modifier.background(Ink.Orange).padding(horizontal = 5.dp, vertical = 1.dp)) { Label("NEW ITEM", color = Ink.OnOrange, size = 9f) }
                    VGap(4.dp)
                    Txt(item.label.uppercase(), Type.title(16.sp), maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Subtitle(toast: Toast?, modifier: Modifier = Modifier) {
    if (toast == null || toast.newItem != null && toast.text.isEmpty()) return
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center) {
        Txt("ISAC  ", Type.label(13.sp, Ink.Orange))
        Txt(toast.text, Type.body(15.sp, weight = FontWeight.SemiBold).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 2f), 6f)))
    }
}
