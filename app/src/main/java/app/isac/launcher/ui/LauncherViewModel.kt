package app.isac.launcher.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.isac.launcher.data.AppEntry
import app.isac.launcher.data.Badges
import app.isac.launcher.data.City
import app.isac.launcher.data.ExploreEvent
import app.isac.launcher.data.Tracking
import app.isac.launcher.data.Battery
import app.isac.launcher.data.CalEvent
import app.isac.launcher.data.LOADOUT_SLOTS
import app.isac.launcher.data.Rarity
import app.isac.launcher.data.WidgetSlot
import app.isac.launcher.data.batteryFlow
import app.isac.launcher.data.minuteTicker
import app.isac.launcher.data.nextAlarmFlow
import app.isac.launcher.graph
import app.isac.launcher.isac.IsacLines
import app.isac.launcher.isac.Command
import app.isac.launcher.isac.CommandParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import app.isac.launcher.data.Sensors
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Overlay {
    data object None : Overlay
    data class Details(val entry: AppEntry) : Overlay
    data class SlotPicker(val entry: AppEntry) : Overlay
    data object Stash : Overlay
    data object Settings : Overlay
    data class WidgetPicker(val slot: WidgetSlot) : Overlay
    data class TextInput(val kind: InputKind) : Overlay
    data object Pulse : Overlay
    data object Voice : Overlay
}

enum class InputKind(val title: String, val hint: String) {
    CALLSIGN("Callsign", "What ISAC calls you"),
    STASH_NAME("Stash name", "Name for your app folder"),
    WEATHER_TOWN("Weather town", "Leave empty to use your location"),
}

/** A centre-screen banner, like AREA UNLOCKED or LEVEL UP. */
data class Banner(val kicker: String, val title: String, val sub: String)

/** A pop-up: a NEW ITEM card, a banner, or an ISAC subtitle line. */
data class Toast(val id: Long, val text: String, val newItem: AppEntry? = null, val banner: Banner? = null)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val g = app.graph

    val settings = g.prefs.settings
    val apps = g.apps.apps
    val usage = g.usage.snapshot
    val loadout = g.prefs.loadout
    val stash = g.prefs.stash
    val junk = g.prefs.junk
    val weather = g.weather.weather
    val weatherProblem = g.weather.problem
    val photos = g.photos.photos
    val hasPhotoFolder = g.photos.hasFolder
    val widgetIds = g.widgets.ids
    val badges = Badges.counts
    val torchOn = g.torch.on

    val battery: StateFlow<Battery> = batteryFlow(app).stateIn(viewModelScope, SharingStarted.Eagerly, Battery())
    val now: StateFlow<Long> = minuteTicker().stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())
    val nextAlarm: StateFlow<Long?> = nextAlarmFlow(app).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- sensors (each one stops listening a few seconds after the screen stops showing it) ----
    val tilt: StateFlow<Pair<Float, Float>> = Sensors.tilt(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), 0f to 0f)
    val heading: StateFlow<Float?> = Sensors.heading(app, g.prefs.lastFix?.first, g.prefs.lastFix?.second)
        .map<Float, Float?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)
    val pressure: StateFlow<Float?> = Sensors.pressure(app)
        .onEach { recordPressure(it) }
        .map<Float, Float?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val stealth: StateFlow<Boolean> = Sensors.stealth(app).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val base = g.prefs.base

    /** Pressure change over the last three hours in hPa, or null until there's enough history. */
    fun pressureTrend(): Float? {
        val h = g.prefs.pressureHistory()
        if (h.size < 2) return null
        val last = h.last()
        val past = h.firstOrNull { last.first - it.first <= 3 * 3_600_000L } ?: return null
        val dt = last.first - past.first
        if (dt < 45 * 60_000L) return null
        return (last.second - past.second) * (3 * 3_600_000f / dt)
    }

    private fun recordPressure(hPa: Float) {
        val t = System.currentTimeMillis()
        g.prefs.addPressure(t, hPa)
        val trend = pressureTrend() ?: return
        if (trend <= -3f && t - g.prefs.lastStormWarning > 6 * 3_600_000L) {
            g.prefs.lastStormWarning = t
            showToast("Pressure falling fast. Storm likely.")
        }
    }

    /** Sets the Base of Operations to where the phone is now. */
    fun setBaseHere() {
        viewModelScope.launch {
            val loc = g.weather.locate()
            if (loc == null) {
                showToast("Couldn't get your location. Check that location access is on.")
            } else {
                g.prefs.setBase(loc.latitude, loc.longitude)
                g.prefs.lastFix = loc.latitude to loc.longitude
                g.haptics.confirm()
                showToast("Base of Operations set.")
            }
        }
    }

    val eventDays = MutableStateFlow<Set<Int>>(emptySet())
    val nextEvent = MutableStateFlow<CalEvent?>(null)

    /** Rarity for every app, by key. */
    val rarity: StateFlow<Map<String, Rarity>> = combine(apps, usage, settings) { list, snap, s ->
        list.associate { it.key to g.usage.rarityOf(it, snap, s.rarityByUsage) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    var overlay by mutableStateOf<Overlay>(Overlay.None)
    var showLoading by mutableStateOf(g.prefs.settings.value.loadingScreen)
    var pulseCooldownUntil by mutableStateOf(0L)
    var photoSkips by mutableStateOf(listOf(0, 0))

    val toast = MutableStateFlow<Toast?>(null)
    private var toastJob: Job? = null

    /** Home button pressed while already home: close overlays and go back to Intel. */
    val homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            g.apps.refresh()
            fillDefaultLoadout()
        }
        viewModelScope.launch {
            g.apps.installed.collect { entry ->
                showToast(IsacLines.newItem(entry.label), entry)
            }
        }
        // Speak up when the charger is connected or disconnected.
        viewModelScope.launch {
            battery.distinctUntilChangedBy { it.charging }.drop(1).collect { b ->
                showToast(IsacLines.charger(b.charging))
            }
        }
        viewModelScope.launch {
            now.collect {
                if (eventDays.value.isEmpty() || LocalDate.now().dayOfMonth == 1) refreshCalendar()
            }
        }
        // Stealth: ISAC reacts when Do Not Disturb turns on or off (e.g. Pixel's Flip to Shhh).
        viewModelScope.launch {
            stealth.drop(1).collect { on ->
                showToast(if (on) "Going dark." else "Back online.")
                if (settings.value.isacVoice && on) g.isac.speak("Going dark.")
            }
        }
        // Overheat warning, once each time the phone crosses the threshold.
        viewModelScope.launch {
            battery.distinctUntilChangedBy { it.hot }.drop(1).collect { b ->
                if (b.hot) showToast("Core temperature high at ${b.tempC?.toInt()}°C. Let the phone cool down.")
            }
        }
        viewModelScope.launch { settings.collect { g.haptics.enabled = it.haptics } }
        viewModelScope.launch {
            g.exploration.events.collect { e ->
                when (e) {
                    is ExploreEvent.CityUnlocked -> announceCity(e.city)
                    is ExploreEvent.LevelUp -> if (e.level > g.prefs.announcedLevel) announceLevel(e.level)
                    ExploreEvent.ArrivedHome -> announceArrival()
                }
            }
        }
    }

    fun onResume() {
        viewModelScope.launch {
            g.usage.refresh()
            if (apps.value.isEmpty()) g.apps.refresh()
            autoEquipFromUsage()
        }
        viewModelScope.launch { g.weather.refresh() }
        viewModelScope.launch { g.photos.refresh() }
        viewModelScope.launch { refreshCalendar() }
        catchUpExploration()
        revealHere()
    }

    fun refreshCalendar() {
        viewModelScope.launch {
            val d = LocalDate.now()
            eventDays.value = g.calendar.eventDays(d.year, d.monthValue)
            nextEvent.value = g.calendar.nextEvent()
        }
    }

    fun refreshWeather(force: Boolean = true) = viewModelScope.launch { g.weather.refresh(force) }

    fun launch(entry: AppEntry) {
        overlay = Overlay.None
        g.apps.launch(entry)
    }

    /** Pulse: opens the radar and scans for nearby devices. */
    fun pulse() {
        if (overlay == Overlay.Pulse) return
        overlay = Overlay.Pulse
        startPulseScan()
    }

    fun startPulseScan() {
        if (!g.scanner.hasPermission()) return
        pulseCooldownUntil = System.currentTimeMillis() + 6_000
        g.haptics.pulse()
        g.scanner.pulse { n ->
            val line = "Pulse complete. $n signal${if (n == 1) "" else "s"} in range."
            showToast(line)
            if (settings.value.isacVoice) g.isac.speak(line)
        }
    }

    /** Hold Pulse: listen for a voice command. */
    fun listen() {
        overlay = Overlay.Voice
    }

    /** Download progress of the offline voice pack (0 to 1), or null when not downloading. */
    var voicePackProgress by mutableStateOf<Float?>(null)

    fun downloadVoicePack() {
        if (voicePackProgress != null || g.offlineVoice.installed) return
        voicePackProgress = 0f
        viewModelScope.launch {
            val ok = g.offlineVoice.download { p -> voicePackProgress = p }
            voicePackProgress = null
            showToast(if (ok) "Voice pack installed. Hold Pulse to talk to ISAC." else "The voice pack download didn't finish. Check your connection and try again.")
        }
    }

    fun statusLine(): String {
        val b = battery.value
        return IsacLines.status(System.currentTimeMillis(), b.percent, b.charging, nextAlarm.value, settings.value.use24h)
    }

    /** Tabs to switch to, from voice commands. */
    val navEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Carries out a spoken command and returns what ISAC says back. */
    fun runCommand(text: String, tabs: List<String>): String {
        val ctx = getApplication<Application>()
        fun start(i: Intent) = runCatching { ctx.startActivity(i) }.isSuccess
        val reply = when (val cmd = CommandParser.parse(text, apps.value, tabs)) {
            is Command.Open -> { launch(cmd.app); "Opening ${cmd.app.label}." }
            is Command.Timer -> if (start(CommandParser.timerIntent(cmd.seconds))) "Timer set for ${cmd.spoken}." else "No clock app would take the timer."
            is Command.Alarm -> if (start(CommandParser.alarmIntent(cmd.hour, cmd.minute))) {
                val t = java.time.LocalTime.of(cmd.hour, cmd.minute)
                "Alarm set for ${t.format(java.time.format.DateTimeFormatter.ofPattern(if (settings.value.use24h) "HH:mm" else "h:mm a", java.util.Locale.UK))}."
            } else "No clock app would take the alarm."
            is Command.Torch -> {
                val want = cmd.on ?: !torchOn.value
                if (want != torchOn.value) g.torch.toggle()
                if (want) "Flashlight on." else "Flashlight off."
            }
            Command.Status -> statusLine()
            Command.Weather -> weather.value?.let {
                "It's ${it.tempC} degrees and ${it.description.lowercase()}${if (it.place.isNotBlank()) " in ${it.place}" else ""}. High ${it.highC}, low ${it.lowC}."
            } ?: "No weather data yet."
            Command.Pulse -> { pulse(); "Running Pulse." }
            Command.Home -> base.value?.let { (la, lo) ->
                if (start(CommandParser.navigateIntent(la, lo)) || start(CommandParser.mapIntent(la, lo))) "Plotting a route to the Base of Operations."
                else "No maps app is available."
            } ?: "No Base of Operations set. Set one in Settings, under Intel."
            is Command.Tab -> { navEvents.tryEmit(cmd.index); overlay = Overlay.None; "Opening ${tabs[cmd.index].lowercase()}." }
            is Command.Unknown -> "Command not recognised."
        }
        if (settings.value.isacVoice) g.isac.speak(reply)
        return reply
    }

    fun showToast(text: String, newItem: AppEntry? = null, banner: Banner? = null) {
        toastJob?.cancel()
        val t = Toast(System.nanoTime(), text, newItem, banner)
        toast.value = t
        toastJob = viewModelScope.launch {
            delay(if (banner != null) 4_500 else 5_500)
            if (toast.value?.id == t.id) toast.value = null
        }
    }

    // ---- exploration ----

    val level: StateFlow<Int> = combine(g.exploration.cellCount, g.exploration.cities, g.exploration.distanceKm) { _, _, _ ->
        g.exploration.level
    }.stateIn(viewModelScope, SharingStarted.Eagerly, g.exploration.level)

    /** Shown instead of the normal loading screen after arriving home. */
    var arrivalScreen by mutableStateOf(false)

    /** Adds where the phone is right now to the map. */
    fun revealHere() {
        if (settings.value.tracking == Tracking.OFF) return
        viewModelScope.launch { g.weather.locate()?.let { g.exploration.record(it) } }
    }

    private fun announceCity(city: City) {
        g.prefs.announcedCities = g.exploration.cities.value.size
        g.haptics.reward()
        showToast("New area discovered. ${city.name} added to your intel.",
            banner = Banner("AREA UNLOCKED", city.name.uppercase(), "+150 XP · New area added to your intel"))
        if (settings.value.isacVoice) g.isac.speak("New area discovered. ${city.name} added to your intel.")
    }

    private fun announceLevel(level: Int) {
        g.prefs.announcedLevel = level
        g.haptics.reward()
        showToast("Level up. You are now level $level.", banner = Banner("LEVEL UP", "LEVEL $level", "Exploration rank increased"))
        if (settings.value.isacVoice) g.isac.speak("Level up. You are now level $level.")
    }

    private fun announceArrival() {
        g.prefs.pendingArrival = false
        g.haptics.confirm()
        showToast("Welcome back to the Base of Operations.",
            banner = Banner("WELCOME BACK", "BASE OF OPERATIONS", "Secure. Good to have you home, Agent"))
        if (settings.value.isacVoice) g.isac.speak("Welcome back to the Base of Operations.")
    }

    /** Announces anything that happened while ISAC wasn't on screen. */
    private fun catchUpExploration() {
        if (g.prefs.pendingArrival) {
            g.prefs.pendingArrival = false
            arrivalScreen = true
            showLoading = true
            if (settings.value.isacVoice) g.isac.speak("Welcome back to the Base of Operations.")
        }
        val cities = g.exploration.cities.value
        if (cities.size > g.prefs.announcedCities) announceCity(cities.maxBy { it.firstVisit })
        val lvl = g.exploration.level
        if (lvl > g.prefs.announcedLevel) announceLevel(lvl)
    }

    fun skipPhoto(frame: Int) {
        photoSkips = photoSkips.toMutableList().also { it[frame] = it[frame] + 1 }
    }

    fun setPhotoFolder(uri: Uri) {
        g.photos.setFolder(uri)
        viewModelScope.launch { g.photos.refresh() }
    }

    /** First run: fill the loadout with the phone's main apps, then the most-used ones. */
    private fun fillDefaultLoadout() {
        if (g.prefs.loadoutInitialized) return
        val list = apps.value.filter { !it.isWork }
        if (list.isEmpty()) return
        val pm = getApplication<Application>().packageManager
        fun pkgFor(i: Intent): String? = runCatching { pm.resolveActivity(i, 0)?.activityInfo?.packageName }.getOrNull()
        val ctx = getApplication<Application>()
        val wanted = listOfNotNull(
            pkgFor(Intent(Intent.ACTION_DIAL)),
            runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull(),
            pkgFor(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=home"))),
            pkgFor(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))),
        ).distinct()
        val chosen = ArrayList<String>()
        wanted.forEach { p -> list.firstOrNull { it.pkg == p }?.let { if (it.key !in chosen) chosen += it.key } }
        // The rest stay empty until usage data can fill them with the apps actually used most.
        g.prefs.setLoadout(chosen.take(LOADOUT_SLOTS))
    }

    /** Once usage access is on, fill any empty slots with the most-used apps, one time only. */
    private fun autoEquipFromUsage() {
        val snap = usage.value
        if (!snap.hasAccess || g.prefs.autoEquipDone) return
        val slots = loadout.value.toMutableList()
        val taken = slots.filterNotNull().toMutableSet()
        val ranked = apps.value.filter { !it.isWork && it.key !in taken }
            .sortedByDescending { snap.byPackage[it.pkg]?.foreground7d ?: 0L }
            .filter { (snap.byPackage[it.pkg]?.foreground7d ?: 0L) > 0 }
            .iterator()
        for (i in slots.indices) if (slots[i] == null && ranked.hasNext()) slots[i] = ranked.next().key
        g.prefs.setLoadout(slots)
        g.prefs.autoEquipDone = true
    }
}
