package app.isac.launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

enum class IconStyle(val label: String) { WHITE("WHITE"), ORANGE("ACCENT"), ORIGINAL("ORIGINAL"), PACK("ICON PACK") }

enum class Backdrop(val label: String) {
    CITY_BLURRED("CITY · BLURRED"), CITY("CITY"), WALLPAPER("YOUR WALLPAPER")
}

/** Which page the Home button and a fresh start open on. */
enum class HomePage(val label: String) { INTEL("INTEL"), HUD("FIELD HUD") }

data class Settings(
    val callsign: String = "AGENT",
    val use24h: Boolean = false,
    val iconStyle: IconStyle = IconStyle.WHITE,
    /** Package of the chosen icon pack, used when [iconStyle] is PACK. */
    val iconPack: String = "",
    val backdrop: Backdrop = Backdrop.CITY_BLURRED,
    val rarityByUsage: Boolean = true,
    val isacVoice: Boolean = true,
    val loadingScreen: Boolean = true,
    /** Empty means "use the phone's approximate location". */
    val weatherTown: String = "",
    /** 0 means "only when tapped". */
    val photoMinutes: Int = 30,
    val stashName: String = "STASH",
    /** Hide Android's status bar while the launcher is showing. Swipe down from the top edge to peek. */
    val hideStatusBar: Boolean = true,
    /** Backdrop shifts with the phone's tilt, like a hologram. */
    val parallax: Boolean = true,
    /** Backdrop follows the real sky (sunrise, sunset) and current weather. */
    val livingCity: Boolean = true,
    val shakeToPulse: Boolean = true,
    val haptics: Boolean = true,
    /** When to record places for the exploration map. */
    val tracking: Tracking = Tracking.WHILE_OPEN,
    val homePage: HomePage = HomePage.INTEL,
    /** Double-tap to lock, swipe down for notifications, swipe up for search. */
    val gestures: Boolean = true,
    val morningBriefing: Boolean = true,
    /** Unlocked by levelling up. */
    val accent: Accent = Accent.ORANGE,
    val sky: Sky = Sky.LIVE,
)

/** Number of loadout slots: 3 weapons followed by 6 gear. */
const val LOADOUT_SLOTS = 9

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("isac", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings

    fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        sp.edit {
            putString("callsign", next.callsign)
            putBoolean("use24h", next.use24h)
            putString("iconStyle", next.iconStyle.name)
            putString("iconPack", next.iconPack)
            putString("backdrop", next.backdrop.name)
            putBoolean("rarityByUsage", next.rarityByUsage)
            putBoolean("isacVoice", next.isacVoice)
            putBoolean("loadingScreen", next.loadingScreen)
            putString("weatherTown", next.weatherTown)
            putInt("photoMinutes", next.photoMinutes)
            putString("stashName", next.stashName)
            putBoolean("hideStatusBar", next.hideStatusBar)
            putBoolean("parallax", next.parallax)
            putBoolean("livingCity", next.livingCity)
            putBoolean("shakeToPulse", next.shakeToPulse)
            putBoolean("haptics", next.haptics)
            putString("tracking", next.tracking.name)
            putString("homePage", next.homePage.name)
            putBoolean("gestures", next.gestures)
            putBoolean("morningBriefing", next.morningBriefing)
            putString("accent", next.accent.name)
            putString("sky", next.sky.name)
        }
        _settings.value = next
    }

    private fun readSettings(): Settings {
        val d = Settings()
        return Settings(
            callsign = sp.getString("callsign", d.callsign) ?: d.callsign,
            use24h = sp.getBoolean("use24h", d.use24h),
            iconStyle = enumOr(sp.getString("iconStyle", null), d.iconStyle),
            iconPack = sp.getString("iconPack", "") ?: "",
            backdrop = enumOr(sp.getString("backdrop", null), d.backdrop),
            rarityByUsage = sp.getBoolean("rarityByUsage", d.rarityByUsage),
            isacVoice = sp.getBoolean("isacVoice", d.isacVoice),
            loadingScreen = sp.getBoolean("loadingScreen", d.loadingScreen),
            weatherTown = sp.getString("weatherTown", d.weatherTown) ?: "",
            photoMinutes = sp.getInt("photoMinutes", d.photoMinutes),
            stashName = sp.getString("stashName", d.stashName) ?: d.stashName,
            hideStatusBar = sp.getBoolean("hideStatusBar", d.hideStatusBar),
            parallax = sp.getBoolean("parallax", d.parallax),
            livingCity = sp.getBoolean("livingCity", d.livingCity),
            shakeToPulse = sp.getBoolean("shakeToPulse", d.shakeToPulse),
            haptics = sp.getBoolean("haptics", d.haptics),
            tracking = enumOr(sp.getString("tracking", null), d.tracking),
            homePage = enumOr(sp.getString("homePage", null), d.homePage),
            gestures = sp.getBoolean("gestures", d.gestures),
            morningBriefing = sp.getBoolean("morningBriefing", d.morningBriefing),
            accent = enumOr(sp.getString("accent", null), d.accent),
            sky = enumOr(sp.getString("sky", null), d.sky),
        )
    }

    // ---- loadout: 9 slots of app keys (null = empty) ----
    private val _loadout = MutableStateFlow(readLoadout())
    val loadout: StateFlow<List<String?>> = _loadout
    val loadoutInitialized: Boolean get() = sp.contains("loadout")

    private fun readLoadout(): List<String?> {
        val raw = sp.getString("loadout", null) ?: return List(LOADOUT_SLOTS) { null }
        val parts = raw.split('\n')
        return List(LOADOUT_SLOTS) { i -> parts.getOrNull(i)?.takeIf { it.isNotBlank() } }
    }

    fun setLoadout(slots: List<String?>) {
        val fixed = List(LOADOUT_SLOTS) { slots.getOrNull(it) }
        sp.edit { putString("loadout", fixed.joinToString("\n") { it ?: "" }) }
        _loadout.value = fixed
    }

    /** Puts [key] in [slot], removing it from any other slot first. */
    fun equip(slot: Int, key: String?) {
        val next = _loadout.value.map { if (it == key) null else it }.toMutableList()
        next[slot] = key
        setLoadout(next)
    }

    // ---- stash (one app folder) ----
    private val _stash = MutableStateFlow(readList("stash"))
    val stash: StateFlow<List<String>> = _stash

    fun toggleStash(key: String) {
        val next = if (key in _stash.value) _stash.value - key else _stash.value + key
        sp.edit { putString("stash", next.joinToString("\n")) }
        _stash.value = next
    }

    // ---- junk (hidden from inventory) ----
    private val _junk = MutableStateFlow(readList("junk").toSet())
    val junk: StateFlow<Set<String>> = _junk

    fun toggleJunk(key: String) {
        val next = if (key in _junk.value) _junk.value - key else _junk.value + key
        sp.edit { putString("junk", next.joinToString("\n")) }
        _junk.value = next
    }

    fun clearJunk() {
        sp.edit { remove("junk") }
        _junk.value = emptySet()
    }

    private fun readList(name: String): List<String> =
        (sp.getString(name, "") ?: "").split('\n').filter { it.isNotBlank() }

    // ---- misc ----
    /** First day the launcher ran. */
    val firstRun: Long = sp.getLong("firstRun", 0L).takeIf { it > 0 }
        ?: System.currentTimeMillis().also { now -> sp.edit { putLong("firstRun", now) } }

    var photoTree: String?
        get() = sp.getString("photoTree", null)
        set(v) = sp.edit { putString("photoTree", v) }

    fun widgetId(slot: WidgetSlot): Int =
        // "widgetId" is the single slot from the first build; it was the work status slot.
        sp.getInt("widget_${slot.key}", if (slot == WidgetSlot.WORK) sp.getInt("widgetId", -1) else -1)

    fun setWidgetId(slot: WidgetSlot, id: Int) = sp.edit { putInt("widget_${slot.key}", id) }

    var autoEquipDone: Boolean
        get() = sp.getBoolean("autoEquipDone", false)
        set(v) = sp.edit { putBoolean("autoEquipDone", v) }

    // ---- places ----
    private val _base = MutableStateFlow(readPoint("base"))
    /** Base of Operations (home), set by the user. */
    val base: StateFlow<Pair<Double, Double>?> = _base

    fun setBase(lat: Double, lon: Double) {
        sp.edit { putString("base", "$lat,$lon") }
        _base.value = lat to lon
    }

    /** The last place the phone reported, for the sky, the compass and the map. */
    var lastFix: Pair<Double, Double>?
        get() = readPoint("lastFix")
        set(v) = sp.edit { putString("lastFix", v?.let { "${it.first},${it.second}" }) }

    private fun readPoint(name: String): Pair<Double, Double>? = sp.getString(name, null)?.split(',')?.let {
        val a = it.getOrNull(0)?.toDoubleOrNull()
        val b = it.getOrNull(1)?.toDoubleOrNull()
        if (a != null && b != null) a to b else null
    }

    /** Pressure readings as "epochMillis:hPa", oldest first, kept for six hours. */
    fun addPressure(time: Long, hPa: Float) {
        val keep = pressureHistory().filter { time - it.first < 6 * 3_600_000L }
        if (keep.isNotEmpty() && time - keep.last().first < 10 * 60_000L) return
        val next = keep + (time to hPa)
        sp.edit { putString("pressure", next.joinToString(";") { "${it.first}:${it.second}" }) }
    }

    fun pressureHistory(): List<Pair<Long, Float>> = (sp.getString("pressure", "") ?: "").split(';').mapNotNull {
        val p = it.split(':')
        val t = p.getOrNull(0)?.toLongOrNull()
        val v = p.getOrNull(1)?.toFloatOrNull()
        if (t != null && v != null) t to v else null
    }

    val tracking: Tracking get() = _settings.value.tracking

    /** Set when you arrive home in the background; the next time ISAC opens it welcomes you back. */
    var pendingArrival: Boolean
        get() = sp.getBoolean("pendingArrival", false)
        set(v) = sp.edit { putBoolean("pendingArrival", v) }

    /** How many cities and which level ISAC has already announced, so nothing is announced twice. */
    var announcedCities: Int
        get() = sp.getInt("announcedCities", 0)
        set(v) = sp.edit { putInt("announcedCities", v) }

    var announcedLevel: Int
        get() = sp.getInt("announcedLevel", 1)
        set(v) = sp.edit { putInt("announcedLevel", v) }

    var lastStormWarning: Long
        get() = sp.getLong("lastStormWarning", 0L)
        set(v) = sp.edit { putLong("lastStormWarning", v) }

    var lastBriefingDay: Long
        get() = sp.getLong("lastBriefingDay", 0L)
        set(v) = sp.edit { putLong("lastBriefingDay", v) }

    var weatherCache: String?
        get() = sp.getString("weatherCache", null)
        set(v) = sp.edit { putString("weatherCache", v) }

    /** Raw access for the daily directives, which keep a handful of counters. */
    fun getLong(key: String, def: Long = 0L) = sp.getLong(key, def)
    fun putLong(key: String, v: Long) = sp.edit { putLong(key, v) }
    fun getString(key: String): String? = sp.getString(key, null)
    fun putString(key: String, v: String) = sp.edit { putString(key, v) }

    // ---- backup ----

    /** Keys that only make sense on this phone, so they're left out of backups. */
    private fun portable(key: String) = !key.startsWith("widget") && key != "photoTree" && key != "weatherCache"

    fun exportJson(): JSONObject {
        val o = JSONObject()
        sp.all.forEach { (k, v) ->
            if (!portable(k) || v == null) return@forEach
            val (t, value) = when (v) {
                is Boolean -> "b" to v
                is Int -> "i" to v
                is Long -> "l" to v
                is Float -> "f" to v.toDouble()
                is String -> "s" to v
                else -> return@forEach
            }
            o.put(k, JSONObject().put("t", t).put("v", value))
        }
        return o
    }

    /** Replaces the saved settings with a backup. ISAC restarts afterwards to load them. */
    fun importJson(o: JSONObject) {
        sp.edit(commit = true) {
            sp.all.keys.filter(::portable).forEach { remove(it) }
            o.keys().forEach { k ->
                if (!portable(k)) return@forEach
                val e = o.optJSONObject(k) ?: return@forEach
                when (e.optString("t")) {
                    "b" -> putBoolean(k, e.optBoolean("v"))
                    "i" -> putInt(k, e.optInt("v"))
                    "l" -> putLong(k, e.optLong("v"))
                    "f" -> putFloat(k, e.optDouble("v").toFloat())
                    "s" -> putString(k, e.optString("v"))
                }
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        runCatching { enumValueOf<T>(name ?: "") }.getOrDefault(fallback)
}
