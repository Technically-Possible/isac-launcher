package app.isac.launcher.ui.overlays

import android.Manifest
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.Backdrop
import app.isac.launcher.data.IconStyle
import app.isac.launcher.data.WidgetSlot
import app.isac.launcher.data.Accent
import app.isac.launcher.data.GestureService
import app.isac.launcher.data.HomePage
import app.isac.launcher.data.Faction
import app.isac.launcher.data.IconPack
import app.isac.launcher.data.Rewards
import app.isac.launcher.data.Sky
import app.isac.launcher.data.ExplorationTracker
import app.isac.launcher.data.Tracking
import androidx.compose.runtime.remember
import app.isac.launcher.ui.InputKind
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.components.CityBackdrop
import app.isac.launcher.ui.components.IsacTabs
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.OptionRow
import app.isac.launcher.ui.components.Prompt
import app.isac.launcher.ui.components.PromptBar
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type
import app.isac.launcher.ui.theme.Lex

private class Opt(
    val label: String,
    val value: String?,
    val help: String,
    val danger: Boolean = false,
    val cycle: ((Int) -> Unit)? = null,
    val action: (() -> Unit)? = null,
)

private fun <T> List<T>.step(cur: T, dir: Int): T = this[((indexOf(cur) + dir) % size + size) % size]

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val hasFolder by vm.hasPhotoFolder.collectAsStateWithLifecycle()
    val widgetIds by vm.widgetIds.collectAsStateWithLifecycle()
    fun slotValue(slot: WidgetSlot) = when {
        (widgetIds[slot] ?: -1) != -1 -> "WIDGET"
        slot.isPhoto && hasFolder -> "FOLDER"
        else -> "EMPTY"
    }
    val junk by vm.junk.collectAsStateWithLifecycle()
    val base by vm.base.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf("") }
    val update = vm.g.prefs::update

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setPhotoFolder(uri)
    }
    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshCalendar() }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshWeather() }
    var eraseArmed by remember { mutableStateOf(false) }
    val askBackground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ExplorationTracker.start(context) }
    val askFineForAlways = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ExplorationTracker.hasLocation(context) && !ExplorationTracker.hasBackground(context) && android.os.Build.VERSION.SDK_INT >= 29) {
            askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else ExplorationTracker.start(context)
    }
    fun open(action: String) = runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    val level by vm.level.collectAsStateWithLifecycle()
    val packs = remember { IconPack.installed(context) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val loadBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    val nextUnlock = Rewards.nextUnlock(level)?.let { " Next unlock: $it." } ?: " Everything is unlocked."
    val photoSteps = listOf(15, 30, 60, 0)
    val tabs = listOf("AGENT", "DISPLAY", "INTEL", "SYSTEM")
    val options: List<Opt> = when (tab) {
        0 -> listOf(
            Opt("Callsign", s.callsign.uppercase(), "What ISAC calls you, on the loading screen and in the header.",
                action = { vm.overlay = Overlay.TextInput(InputKind.CALLSIGN) }),
            Opt("ISAC voice", if (s.isacVoice) "ON" else "OFF", "ISAC reads your status aloud when you use the Pulse skill. It never speaks on its own.",
                cycle = { update { it.copy(isacVoice = !it.isacVoice) } }),
            Opt("Loading screen", if (s.loadingScreen) "ON" else "OFF", "Shows the Base of Operations screen with a tip when the launcher starts after a restart.",
                cycle = { update { it.copy(loadingScreen = !it.loadingScreen) } }),
            Opt("Offline voice pack",
                when {
                    vm.g.offlineVoice.installed -> "INSTALLED"
                    vm.voicePackProgress != null -> "${((vm.voicePackProgress ?: 0f) * 100).toInt()}%"
                    else -> "DOWNLOAD ${app.isac.launcher.isac.OfflineVoice.MODEL_MB} MB"
                },
                "ISAC's own speech recognition for voice commands (hold Pulse). It works offline, so what you say never leaves the phone. One-time download from alphacephei.com (Vosk, open source).",
                action = { vm.downloadVoicePack() }),
            Opt("Morning briefing", if (s.morningBriefing) "ON" else "OFF", "The first time you open ISAC each morning, it reads out the weather, your first event and your battery.",
                cycle = { update { it.copy(morningBriefing = !it.morningBriefing) } }),
            Opt("Shake to Pulse", if (s.shakeToPulse) "ON" else "OFF", "Give the phone a firm shake while ISAC is on screen to run a Pulse.",
                cycle = { update { it.copy(shakeToPulse = !it.shakeToPulse) } }),
            Opt("Haptics", if (s.haptics) "ON" else "OFF", "Short vibrations when you switch tabs, equip or dismantle items, and run a Pulse.",
                cycle = { update { it.copy(haptics = !it.haptics) } }),
        )
        1 -> listOfNotNull(
            Opt("Faction", s.faction.label,
                "Division is ISAC: warm orange rings. Black Tusk switches everything to DIAMOND: crimson, angular and cold. Rogue and Emerald Network are still being built. Tap to see them all.",
                action = { vm.overlay = Overlay.Faction },
                cycle = { d -> update { it.copy(faction = Faction.entries.filter { f -> f.ready }.step(it.faction, d), factionChosen = true) } }),
            Opt("Home page", s.homePage.label, "What the first tab shows. Intel is your dashboard. Field HUD shows the city with only the essentials on top, like the game's HUD.",
                cycle = { d -> update { it.copy(homePage = HomePage.entries.toList().step(it.homePage, d)) } }),
            if (Ink.diamond) Opt("Accent colour", "TACTICAL CRIMSON", "Black Tusk always runs in crimson. Accent colours you've unlocked apply when you're with the Division.")
            else Opt("Accent colour", s.accent.label, "Recolours the whole interface. New colours unlock as you level up.$nextUnlock",
                cycle = { d -> update { it.copy(accent = Rewards.accents(level).step(it.accent.takeIf { a -> a.level <= level } ?: Accent.ORANGE, d)) } }),
            if (Ink.diamond) Opt("Optic feeds", if (s.opticMono) "MONO" else "COLOUR", "How DIAMOND shows your photo frames: black-and-white optic feeds, or your photos in full colour.",
                cycle = { update { it.copy(opticMono = !it.opticMono) } }) else null,
            if (Ink.diamond) Opt("City sky", "VOID GRID", "Black Tusk replaces the city with DIAMOND's void grid. City skies apply when you're with the Division.")
            else Opt("City sky", s.sky.label, "Live follows the real time of day and weather. Other skies unlock as you level up.$nextUnlock",
                cycle = { d -> update { it.copy(sky = Rewards.skies(level).step(it.sky.takeIf { k -> k.level <= level } ?: Sky.LIVE, d)) } }),
            Opt("Icon pack", packs.firstOrNull { it.pkg == s.iconPack }?.label?.uppercase() ?: if (packs.isEmpty()) "NONE INSTALLED" else "NONE",
                "Use an icon pack made for Nova or similar launchers. Picking one switches App icons to Icon pack.",
                cycle = { d ->
                    if (packs.isNotEmpty()) {
                        val options = listOf("") + packs.map { it.pkg }
                        val next = options.step(s.iconPack.takeIf { it in options } ?: "", d)
                        vm.g.icons.clear()
                        update { it.copy(iconPack = next, iconStyle = if (next.isEmpty()) IconStyle.WHITE else IconStyle.PACK) }
                    }
                }),
            Opt("Background", s.backdrop.label, "The dusk city from the designs, blurred like the game's menus, or sharp. Or show your own wallpaper behind everything.",
                cycle = { d -> update { it.copy(backdrop = Backdrop.entries.toList().step(it.backdrop, d)) } }),
            Opt("App icons", s.iconStyle.label, "White or accent-coloured line icons use each app's themed icon where it has one. Original keeps every app's own colours. Icon pack uses the pack chosen above.",
                cycle = { d ->
                    val styles = IconStyle.entries.filter { it != IconStyle.PACK || s.iconPack.isNotEmpty() }
                    update { it.copy(iconStyle = styles.step(it.iconStyle, d)) }
                }),
            Opt("Item rarity", if (s.rarityByUsage) "BY USAGE" else "OFF", "Colors each app by how often you use it. This needs Usage Access, which you turn on once in Android settings.",
                cycle = { update { it.copy(rarityByUsage = !it.rarityByUsage) } }),
            Opt("Clock", if (s.use24h) "24-HOUR" else "12-HOUR", "How times are shown across the launcher.",
                cycle = { update { it.copy(use24h = !it.use24h) } }),
            Opt("Parallax", if (s.parallax) "ON" else "OFF", "The city behind the menus shifts as you tilt the phone, like a hologram.",
                cycle = { update { it.copy(parallax = !it.parallax) } }),
            Opt("Living city", if (s.livingCity) "ON" else "OFF", "The sky follows the real sunrise and sunset, with stars and the moon at night, and the current weather plays over the city.",
                cycle = { update { it.copy(livingCity = !it.livingCity) } }),
            Opt("Status bar", if (s.hideStatusBar) "HIDDEN" else "SHOWN",
                "Hides Android's status bar on the launcher for a cleaner screen. Swipe down from the top edge to see it for a moment.",
                cycle = { update { it.copy(hideStatusBar = !it.hideStatusBar) } }),
        )
        2 -> listOf(
            Opt("Exploration tracking", s.tracking.label,
                "Records the places you visit for the fog-of-war map. Always keeps mapping in the background (Android asks to allow location all the time). While open only maps when ISAC is on screen. Everything stays on your phone.",
                cycle = { d ->
                    val next = Tracking.entries.toList().step(s.tracking, d)
                    update { it.copy(tracking = next) }
                    when (next) {
                        Tracking.ALWAYS -> when {
                            !ExplorationTracker.hasLocation(context) ->
                                askFineForAlways.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            !ExplorationTracker.hasBackground(context) && android.os.Build.VERSION.SDK_INT >= 29 ->
                                askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            else -> ExplorationTracker.start(context)
                        }
                        Tracking.WHILE_OPEN -> ExplorationTracker.start(context)
                        Tracking.OFF -> ExplorationTracker.stop(context)
                    }
                }),
            Opt("Erase exploration", if (eraseArmed) "TAP AGAIN TO ERASE" else "${vm.g.exploration.cellCount.value} CELLS",
                "Deletes your uncovered map, distance travelled and unlocked cities. This can't be undone.",
                danger = true,
                action = {
                    if (eraseArmed) {
                        vm.g.exploration.erase()
                        eraseArmed = false
                        vm.showToast("Exploration data erased.")
                    } else eraseArmed = true
                }),
            Opt("Base of Operations", if (base == null) "SET HERE" else "MOVE HERE",
                "Your home. The compass shows a waypoint to it with the distance. Tap while you're at home to set it to where you are now.",
                action = { vm.setBaseHere() }),
            Opt("Photo frame 1", slotValue(WidgetSlot.PHOTO1),
                "Holds your photo widget, or photos from a folder. You can also long-press the frame on the Intel page to change it.",
                action = { vm.overlay = Overlay.WidgetPicker(WidgetSlot.PHOTO1) }),
            Opt("Photo frame 2", slotValue(WidgetSlot.PHOTO2),
                "Holds your photo widget, or photos from a folder. You can also long-press the frame on the Intel page to change it.",
                action = { vm.overlay = Overlay.WidgetPicker(WidgetSlot.PHOTO2) }),
            Opt("Work status", slotValue(WidgetSlot.WORK), "Holds any Android widget, such as Slack's custom status widget. It sits at the bottom of the Loadout page.",
                action = { vm.overlay = Overlay.WidgetPicker(WidgetSlot.WORK) }),
            Opt("Photo folder", if (hasFolder) "CHANGE" else "CHOOSE", "For frames without a widget: the folder they cycle through. Only this folder is shared with the launcher.",
                action = { pickFolder.launch(null) }),
            Opt("New photo every", photoSteps.let { if (s.photoMinutes == 0) "ONLY ON TAP" else if (s.photoMinutes >= 60) "1 HOUR" else "${s.photoMinutes} MIN" },
                "How often the frames change on their own. You can always tap a frame to skip ahead.",
                cycle = { d -> update { it.copy(photoMinutes = photoSteps.step(it.photoMinutes.takeIf { m -> m in photoSteps } ?: 30, d)) } }),
            Opt("Weather location", s.weatherTown.ifBlank { "PHONE LOCATION" }.uppercase(),
                "Leave the town empty to use your phone's approximate location. Weather comes from Open-Meteo, a free service with no account.",
                action = { vm.overlay = Overlay.TextInput(InputKind.WEATHER_TOWN) }),
            Opt("Location access", if (vm.g.weather.hasLocationPermission()) "ALLOWED" else "ALLOW", "Only needed when no town is set.",
                action = { locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }),
            Opt("Calendar access", if (vm.g.calendar.hasAccess()) "ALLOWED" else "ALLOW", "Shows dots on days with events and your next event under Directives.",
                action = { calPerm.launch(Manifest.permission.READ_CALENDAR) }),
        )
        else -> listOfNotNull(
            Opt("Set as home app", null, "Opens Android's picker so you can choose SHD ISAC as your home screen. You can switch back to Nova any time.",
                action = { open(AndroidSettings.ACTION_HOME_SETTINGS) }),
            Opt("Gestures", if (s.gestures) "ON" else "OFF", "On the home and loadout pages: swipe down for notifications, swipe up to search, double-tap an empty spot to lock.",
                cycle = { update { it.copy(gestures = !it.gestures) } }),
            Opt("Lock and notification gestures", if (GestureService.enabled) "ON" else "ALLOW",
                "Double-tap to lock needs the SHD ISAC gestures accessibility service, the same way other launchers do it. It doesn't read anything on screen. If Android says the setting is restricted, open App info for SHD ISAC, tap the menu and choose Allow restricted settings.",
                action = { open(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) }),
            Opt("Save backup", null, "Saves your settings, loadout, stash and exploration map to a file you choose. Widgets and photo folders aren't included, because they're tied to this phone.",
                action = { saveBackup.launch("isac-backup.json") }),
            Opt("Restore backup", null, "Loads a backup file and restarts ISAC. This replaces your current settings and map.",
                action = { loadBackup.launch(arrayOf("application/json", "*/*")) }),
            Opt("Usage access", if (usage.hasAccess) "ALLOWED" else "ALLOW", "Lets the launcher see how often you use apps, for rarity and sorting. Nothing leaves your phone.",
                action = { open(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS) }),
            Opt("Notification counters", if (app.isac.launcher.data.Badges.hasAccess(context)) "ON" else "ALLOW",
                "Shows an orange count on apps with unread notifications. Turn on notification access for SHD ISAC. It only counts notifications; it never reads or stores what they say.",
                action = {
                    val detail = if (android.os.Build.VERSION.SDK_INT >= 30) {
                        Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                app.isac.launcher.data.Badges.component(context).flattenToString())
                    } else null
                    val opened = detail != null && runCatching {
                        context.startActivity(detail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isSuccess
                    if (!opened) open(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }),
            if (junk.isNotEmpty()) Opt("Junk items", "${junk.size} HIDDEN", "Puts every item you marked as junk back in your inventory.",
                action = { vm.g.prefs.clearJunk() }) else null,
            Opt("Updates",
                vm.update?.let { "INSTALL ${it.version}" } ?: if (vm.updateChecked) "UP TO DATE" else "CHECK NOW",
                "Checks the GitHub releases page for a newer version. When there is one, tap to download it; Android asks you to confirm the install, and the first time to allow SHD ISAC to install apps. Your setup is kept.",
                action = {
                    val u = vm.update
                    if (u != null) { app.isac.launcher.data.Updates.install(context, u); vm.showToast("Downloading version ${u.version}…") }
                    else vm.checkForUpdate(manual = true)
                }),
            Opt("Check for updates", if (s.checkUpdates) "ON" else "OFF", "Looks for a new release when ISAC starts, at most twice a day. Only GitHub's public releases page is contacted.",
                cycle = { update { it.copy(checkUpdates = !it.checkUpdates) } }),
            Opt("Android settings", null, "Opens the phone's main settings.", action = { open(AndroidSettings.ACTION_SETTINGS) }),
            Opt("Credits", null, "Who made SHD ISAC, a link to their site, and the open-source projects and free services it's built on.",
                action = { vm.overlay = Overlay.Credits }),
        )
    }
    val focused = options.firstOrNull { it.label == selected } ?: options.first()

    Box(Modifier.fillMaxSize()) {
        CityBackdrop(Backdrop.CITY_BLURRED)
        // With the status bar hidden, the title row sits at the top edge: its text is at the far left
        // and right, clear of a centred camera cutout.
        val atTop = s.hideStatusBar && app.isac.launcher.ui.components.topCutout() != null
        Column(Modifier.fillMaxSize().then(if (atTop) Modifier else Modifier.windowInsetsPadding(app.isac.launcher.ui.components.topInsets()))) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (atTop) 10.dp else 14.dp), verticalAlignment = Alignment.Bottom) {
                Txt("SETTINGS", Type.title(28.sp))
                Spacer(Modifier.weight(1f))
                Label("${Lex.ai} ${app.isac.launcher.ui.components.appVersion()}", color = Ink.G2, size = 11f)
            }
            IsacTabs(tabs, tab, { tab = it; selected = "" }, Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    val isSel = o === focused
                    OptionRow(
                        o.label, o.value, isSel,
                        onClick = {
                            selected = o.label
                            o.action?.invoke() ?: o.cycle?.invoke(1)
                        },
                        onPrev = o.cycle?.let { c -> { selected = o.label; c(-1) } },
                        onNext = o.cycle?.let { c -> { selected = o.label; c(1) } },
                        danger = o.danger,
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    .background(Ink.Glass).padding(14.dp),
            ) {
                Label(focused.label)
                VGap(6.dp)
                Txt(focused.help, Type.body(14.sp, Ink.G2))
            }
            PromptBar(
                listOf(
                    Prompt("SET AS HOME APP", IsacIcons.Grid) { open(AndroidSettings.ACTION_HOME_SETTINGS) },
                    Prompt("BACK", key = "‹") { vm.overlay = Overlay.None },
                ),
                Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}
