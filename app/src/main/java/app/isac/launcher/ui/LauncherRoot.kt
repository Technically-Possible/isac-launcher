package app.isac.launcher.ui

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.WidgetSlot
import app.isac.launcher.data.Backdrop
import app.isac.launcher.data.GestureService
import app.isac.launcher.data.HomePage
import app.isac.launcher.data.Sky
import app.isac.launcher.ui.overlays.ShortcutPanel
import app.isac.launcher.ui.pages.HudPage
import androidx.compose.foundation.gestures.detectTapGestures
import app.isac.launcher.data.Moon
import app.isac.launcher.data.Sensors
import app.isac.launcher.data.SkyPhase
import app.isac.launcher.data.Sun
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.repeatOnLifecycle
import app.isac.launcher.ui.components.CityBackdrop
import app.isac.launcher.ui.components.IsacTabs
import app.isac.launcher.ui.components.Prompt
import app.isac.launcher.ui.components.PromptBar
import app.isac.launcher.ui.components.topInsets
import app.isac.launcher.ui.components.CutoutTabs
import app.isac.launcher.ui.components.topCutout
import app.isac.launcher.ui.overlays.ItemDetails
import app.isac.launcher.ui.overlays.PulseOverlay
import app.isac.launcher.ui.overlays.VoiceOverlay
import app.isac.launcher.ui.overlays.LoadingScreen
import app.isac.launcher.ui.overlays.SettingsScreen
import app.isac.launcher.ui.overlays.SlotPicker
import app.isac.launcher.ui.overlays.StashPanel
import app.isac.launcher.ui.overlays.Subtitle
import app.isac.launcher.ui.overlays.TextInputPanel
import app.isac.launcher.ui.overlays.ToastLayer
import app.isac.launcher.ui.overlays.WidgetPicker
import app.isac.launcher.ui.pages.IntelPage
import app.isac.launcher.ui.pages.InventoryPage
import app.isac.launcher.ui.pages.LoadoutPage
import app.isac.launcher.ui.pages.MapPage
import app.isac.launcher.ui.theme.IsacIcons
import kotlinx.coroutines.launch

private val BASE_TABS = listOf("INTEL", "LOADOUT", "INVENTORY", "MAP")

/** The sky and weather to draw for the chosen (and unlocked) sky. */
private fun skyFor(sky: Sky, living: Boolean, live: SkyPhase, liveWeather: Int?): Pair<SkyPhase, Int?> = when (sky) {
    Sky.LIVE -> if (living) live to liveWeather else SkyPhase.DUSK to null
    Sky.DUSK -> SkyPhase.DUSK to null
    Sky.NIGHT -> SkyPhase.NIGHT to null
    Sky.WINTER -> SkyPhase.DAY to 73
    Sky.DARK_ZONE -> SkyPhase.NIGHT to 95
}

@Composable
fun LauncherRoot(vm: LauncherViewModel, onPickWidget: (WidgetSlot, AppWidgetProviderInfo) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val hudHome = settings.homePage == HomePage.HUD
    val TABS = if (hudHome) listOf("HUD") + BASE_TABS.drop(1) else BASE_TABS
    val powerSave by vm.powerSave.collectAsStateWithLifecycle()
    val pager = rememberPagerState { TABS.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = LocalFocusManager.current

    LaunchedEffect(Unit) {
        vm.homeEvents.collect {
            focus.clearFocus()
            if (vm.overlay != Overlay.None) vm.overlay = Overlay.None else pager.animateScrollToPage(0)
        }
    }
    LaunchedEffect(Unit) { vm.navEvents.collect { pager.animateScrollToPage(it.coerceIn(0, TABS.lastIndex)) } }

    var firstPage by remember { mutableStateOf(true) }
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage != 2) focus.clearFocus()
        if (firstPage) firstPage = false else vm.g.haptics.tick()
    }

    // Shake to Pulse, only while ISAC is on screen.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    if (settings.shakeToPulse) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                Sensors.shakes(context).collect { vm.pulse() }
            }
        }
    }

    BackHandler(enabled = vm.overlay != Overlay.None) {
        vm.overlay = when (vm.overlay) {
            is Overlay.SlotPicker -> (vm.overlay as Overlay.SlotPicker).entry.let { Overlay.Details(it) }
            is Overlay.TextInput -> Overlay.Settings
            else -> Overlay.None
        }
    }
    BackHandler(enabled = vm.overlay == Overlay.None && pager.currentPage != 0) {
        scope.launch { pager.animateScrollToPage(0) }
    }

    Box(Modifier.fillMaxSize()) {
        // Battery saver pauses the tilt effect and weather animation.
        val tilt = if (settings.parallax && !powerSave) vm.tilt.collectAsStateWithLifecycle() else null
        val now by vm.now.collectAsStateWithLifecycle()
        val weather by vm.weather.collectAsStateWithLifecycle()
        val fix = remember(now / 600_000) { vm.g.prefs.lastFix }
        val moon = remember(now / 3_600_000) { Moon.at(now) }
        val (phase, weatherCode) = skyFor(settings.sky, settings.livingCity, Sun.phase(now, fix?.first, fix?.second), weather?.code)
        // The Field HUD shows the city sharp, like gameplay; menus blur it.
        val onHud = hudHome && pager.currentPage == 0 && vm.overlay == Overlay.None
        CityBackdrop(
            if (onHud && settings.backdrop == Backdrop.CITY_BLURRED) Backdrop.CITY else settings.backdrop,
            tilt = tilt,
            phase = phase,
            weatherCode = weatherCode,
            moon = moon,
            animate = !powerSave,
        )
        Column(Modifier.fillMaxSize()) {
            val swipeDown = Modifier.pointerInput(Unit) {
                detectVerticalDragGestures { _, drag -> if (drag > 18f) expandNotifications(context) }
            }
            val selectTab: (Int) -> Unit = { scope.launch { pager.animateScrollToPage(it) } }
            val cutout = topCutout()
            if (settings.hideStatusBar && cutout != null) {
                // Status bar hidden: the tabs go right up to the top edge, either side of the camera.
                CutoutTabs(TABS, pager.currentPage, selectTab, cutout.first, cutout.second, swipeDown)
            } else {
                Box(swipeDown.windowInsetsPadding(topInsets())) {
                    IsacTabs(TABS, pager.currentPage, selectTab, Modifier.padding(horizontal = 14.dp))
                }
            }
            // Gestures on the home and loadout pages: swipe down for notifications, swipe up for search,
            // double-tap an empty spot to lock.
            val gesturePage = settings.gestures && pager.currentPage <= 1
            val gestures = if (!gesturePage) Modifier else Modifier
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total > 140f) expandNotifications(context)
                            else if (total < -140f) scope.launch { pager.animateScrollToPage(2); vm.searchFocus.tryEmit(Unit) }
                        },
                    ) { _, drag -> total += drag }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (!GestureService.lock()) vm.showToast("To lock with a double-tap, turn on SHD ISAC gestures in Settings, under System.")
                    })
                }
            // On the map, drags pan the map instead of changing page; use the tabs to leave it.
            HorizontalPager(
                pager, Modifier.weight(1f).fillMaxWidth().then(gestures), beyondViewportPageCount = 1,
                userScrollEnabled = pager.currentPage != 3,
            ) { page ->
                when (page) {
                    0 -> if (hudHome) HudPage(vm) else IntelPage(vm)
                    1 -> LoadoutPage(vm, onOpenInventory = { scope.launch { pager.animateScrollToPage(2) } })
                    2 -> InventoryPage(vm)
                    else -> MapPage(vm, active = pager.currentPage == 3)
                }
            }
            Box {
                Subtitle(toast?.takeIf { it.newItem == null && it.banner == null }, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }
            PromptBar(
                buildList {
                    if (pager.currentPage != 1) add(Prompt("PULSE", IsacIcons.Pulse, onLongClick = { vm.listen() }) { vm.pulse() })
                    add(Prompt("SETTINGS", IsacIcons.Gear) { vm.overlay = Overlay.Settings })
                    if (pager.currentPage != 2) add(Prompt("INVENTORY", IsacIcons.Box) { scope.launch { pager.animateScrollToPage(2) } })
                },
                Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
        ToastLayer(toast, Modifier.windowInsetsPadding(topInsets()).padding(top = 48.dp))

        when (val o = vm.overlay) {
            Overlay.None -> Unit
            is Overlay.Details -> ItemDetails(vm, o.entry)
            is Overlay.SlotPicker -> SlotPicker(vm, o.entry)
            Overlay.Stash -> StashPanel(vm)
            Overlay.Settings -> SettingsScreen(vm)
            is Overlay.WidgetPicker -> WidgetPicker(vm, o.slot, onPickWidget)
            is Overlay.TextInput -> TextInputPanel(vm, o.kind)
            is Overlay.Shortcut -> ShortcutPanel(vm, o.info)
            Overlay.Pulse -> PulseOverlay(vm)
            Overlay.Voice -> VoiceOverlay(vm, TABS)
        }

        AnimatedVisibility(vm.showLoading, enter = fadeIn(), exit = fadeOut()) {
            LoadingScreen(vm, arrival = vm.arrivalScreen) { vm.showLoading = false; vm.arrivalScreen = false }
        }
    }
}

/** Opens the notification shade. Uses a hidden platform call, so it quietly does nothing if blocked. */
@SuppressLint("WrongConstant")
private fun expandNotifications(context: Context) {
    if (GestureService.notifications()) return
    runCatching {
        val sbm = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
    }
}
