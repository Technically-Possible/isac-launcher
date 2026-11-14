package app.isac.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import app.isac.launcher.MainActivity
import app.isac.launcher.R
import app.isac.launcher.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/** Map cells are web-map tiles at zoom 18: about 95 m across in the UK. */
object Cells {
    const val ZOOM = 18
    private val N = (1 shl ZOOM).toDouble()

    fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
    fun x(key: Long) = (key shr 32).toInt()
    fun y(key: Long) = key.toInt()

    fun tileX(lon: Double) = floor((lon + 180.0) / 360.0 * N).toInt()
    fun tileY(lat: Double): Int {
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * N).toInt()
    }
    fun lon(x: Double) = x / N * 360.0 - 180.0
    fun lat(y: Double) = Math.toDegrees(atan(sinh(PI * (1 - 2 * y / N))))

    fun metersAcross(lat: Double) = 40_075_016.686 * cos(Math.toRadians(lat)) / N
}

data class City(val name: String, val country: String, val firstVisit: Long, val lat: Double, val lon: Double)

sealed interface ExploreEvent {
    data class CityUnlocked(val city: City) : ExploreEvent
    data class LevelUp(val level: Int) : ExploreEvent
    data object ArrivedHome : ExploreEvent
}

enum class Tracking(val label: String) { ALWAYS("ALWAYS"), WHILE_OPEN("WHILE OPEN"), OFF("OFF") }

/**
 * The fog-of-war record: which map cells you've uncovered, distance travelled, cities unlocked,
 * and the XP and level they add up to. Stored only on the phone, in the app's private files.
 */
class Exploration(private val context: Context, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val cellsFile = File(context.filesDir, "explore_cells.bin")
    private val stateFile = File(context.filesDir, "explore.json")

    private val cells = HashSet<Long>()
    private val _cities = MutableStateFlow<List<City>>(emptyList())
    val cities: StateFlow<List<City>> = _cities

    private val _cellCount = MutableStateFlow(0)
    val cellCount: StateFlow<Int> = _cellCount

    private val _distanceKm = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm

    /** Bumped whenever the uncovered area changes, so the map knows to redraw the fog. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val _events = MutableSharedFlow<ExploreEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ExploreEvent> = _events

    private val _expedition = MutableStateFlow(false)
    val expeditionActive: StateFlow<Boolean> = _expedition

    private var lastFix: Location? = null
    private var lastGeocode: Location? = null
    private var lastGeocodeTime = 0L

    val xp: Int get() = _cellCount.value + _cities.value.size * 150 + (_distanceKm.value * 2).toInt()

    init { load() }

    // ---------------------------------------------------------------- level

    companion object {
        fun levelFor(xp: Int) = 1 + floor(sqrt(xp / 40.0)).toInt()
        fun xpForLevel(level: Int) = 40 * (level - 1) * (level - 1)
        const val REVEAL_METERS = 110.0
    }

    val level: Int get() = levelFor(xp)

    /** Progress through the current level, 0 to 1. */
    fun levelProgress(): Float {
        val l = level
        val a = xpForLevel(l)
        val b = xpForLevel(l + 1)
        return ((xp - a).toFloat() / (b - a)).coerceIn(0f, 1f)
    }

    // ---------------------------------------------------------------- recording

    /** Takes in one location fix from any source. */
    fun record(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > 120f) {
            // Approximate location (a few km out) can't clear fog or measure distance, but it still knows the town.
            if (loc.accuracy <= 5_000f) {
                prefs.lastFix = loc.latitude to loc.longitude
                maybeGeocode(loc)
            }
            return
        }
        val before = level
        val prev = lastFix
        var added = 0
        if (prev != null) {
            val dt = (loc.time - prev.time) / 1000.0
            if (dt <= 0) return
            val d = prev.distanceTo(loc).toDouble()
            val speed = d / dt
            // Ignore GPS jitter, and don't draw lines for flights or long gaps.
            if (d > 20 && speed < 70 && dt < 30 * 60) {
                _distanceKm.value += d / 1000
                if (dt < 20 * 60 && d < 30_000) added += interpolate(prev, loc)
            }
        }
        added += reveal(loc.latitude, loc.longitude, REVEAL_METERS)
        checkArrival(prev, loc)
        lastFix = loc
        prefs.lastFix = loc.latitude to loc.longitude
        if (added > 0) {
            _cellCount.value = cells.size
            _version.value++
            maybeGeocode(loc)
        }
        save()
        val after = level
        if (after > before) _events.tryEmit(ExploreEvent.LevelUp(after))
    }

    private fun interpolate(a: Location, b: Location): Int {
        val d = a.distanceTo(b)
        val steps = (d / 60f).toInt()
        var added = 0
        for (i in 1 until steps) {
            val f = i.toDouble() / steps
            added += reveal(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f, 60.0)
        }
        return added
    }

    private fun reveal(lat: Double, lon: Double, radius: Double): Int {
        val size = Cells.metersAcross(lat)
        val cx = Cells.tileX(lon)
        val cy = Cells.tileY(lat)
        val r = ceil(radius / size).toInt()
        val fresh = ArrayList<Long>()
        synchronized(cells) {
            for (dx in -r..r) for (dy in -r..r) {
                if (sqrt((dx * dx + dy * dy).toDouble()) * size <= radius + size / 2) {
                    val k = Cells.key(cx + dx, cy + dy)
                    if (cells.add(k)) fresh += k
                }
            }
        }
        if (fresh.isNotEmpty()) appendCells(fresh)
        return fresh.size
    }

    private fun checkArrival(prev: Location?, loc: Location) {
        val base = prefs.base.value ?: return
        val out = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, base.first, base.second, out)
        val now = out[0]
        val was = prev?.let {
            Location.distanceBetween(it.latitude, it.longitude, base.first, base.second, out); out[0]
        } ?: return
        if (now < 150 && was > 1_000) {
            prefs.pendingArrival = true
            _events.tryEmit(ExploreEvent.ArrivedHome)
        }
    }

    /** Names the place, at most every few minutes, and unlocks the city if it's new. */
    private fun maybeGeocode(loc: Location) {
        val now = System.currentTimeMillis()
        val last = lastGeocode
        if (last != null && now - lastGeocodeTime < 5 * 60_000 && last.distanceTo(loc) < 3_000) return
        lastGeocode = loc
        lastGeocodeTime = now
        scope.launch {
            val place = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(context, Locale.UK).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                }.getOrNull()
            } ?: return@launch
            val name = place.locality ?: place.subAdminArea ?: return@launch
            val country = place.countryCode ?: ""
            if (_cities.value.none { it.name.equals(name, true) && it.country == country }) {
                val before = level
                val city = City(name, country, now, loc.latitude, loc.longitude)
                _cities.value = _cities.value + city
                save()
                _events.tryEmit(ExploreEvent.CityUnlocked(city))
                if (level > before) _events.tryEmit(ExploreEvent.LevelUp(level))
                if (!Graphless.isUiVisible) notifyCity(city)
            }
        }
    }

    // ---------------------------------------------------------------- fog geometry

    /**
     * The fog as GeoJSON: the whole world with your uncovered area cut out, plus unexplored pockets
     * inside it. Also returns the outlines of the uncovered area, drawn as an orange edge.
     */
    fun fogGeoJson(): Pair<String, String> {
        val loops = traceLoops(synchronized(cells) { HashSet(cells) })
        val outer = loops.filter { it.second > 0 }.map { it.first }
        val inner = loops.filter { it.second < 0 }.map { it.first }
        fun ring(pts: List<Pair<Int, Int>>): JSONArray = JSONArray().apply {
            pts.forEach { (x, y) -> put(JSONArray().put(Cells.lon(x.toDouble())).put(Cells.lat(y.toDouble()))) }
        }
        val world = JSONArray("[[-180,-85],[180,-85],[180,85],[-180,85],[-180,-85]]")
        val polys = JSONArray()
        polys.put(JSONArray().apply { put(world); outer.forEach { put(ring(it)) } })
        inner.forEach { polys.put(JSONArray().put(ring(it))) }
        val fog = JSONObject().put("type", "Feature").put("properties", JSONObject())
            .put("geometry", JSONObject().put("type", "MultiPolygon").put("coordinates", polys))
        val edges = JSONObject().put("type", "Feature").put("properties", JSONObject())
            .put("geometry", JSONObject().put("type", "MultiLineString").put("coordinates", JSONArray().apply { loops.forEach { put(ring(it.first)) } }))
        return fog.toString() to edges.toString()
    }

    /**
     * Traces the outline of the uncovered cells into closed loops of tile corners.
     * Returns each loop with the sign of its area: positive for outer edges, negative for pockets.
     */
    private fun traceLoops(set: Set<Long>): List<Pair<List<Pair<Int, Int>>, Int>> {
        // Directed edges around each uncovered cell, clockwise on screen, wherever the neighbour is fog.
        val out = HashMap<Long, MutableList<Long>>()
        fun v(x: Int, y: Int) = Cells.key(x, y)
        fun edge(ax: Int, ay: Int, bx: Int, by: Int) { out.getOrPut(v(ax, ay)) { ArrayList(1) }.add(v(bx, by)) }
        for (k in set) {
            val x = Cells.x(k)
            val y = Cells.y(k)
            if (Cells.key(x, y - 1) !in set) edge(x, y, x + 1, y)
            if (Cells.key(x + 1, y) !in set) edge(x + 1, y, x + 1, y + 1)
            if (Cells.key(x, y + 1) !in set) edge(x + 1, y + 1, x, y + 1)
            if (Cells.key(x - 1, y) !in set) edge(x, y + 1, x, y)
        }
        val loops = ArrayList<Pair<List<Pair<Int, Int>>, Int>>()
        while (out.isNotEmpty()) {
            val start = out.keys.first()
            val pts = ArrayList<Pair<Int, Int>>()
            var cur = start
            var dirX = 0
            var dirY = 0
            var guard = 0
            do {
                val nexts = out[cur] ?: break
                // Where two cells touch only at a corner, turn right so each region stays its own loop.
                val next = if (nexts.size == 1) nexts[0] else nexts.minByOrNull { n ->
                    val nx = Cells.x(n) - Cells.x(cur)
                    val ny = Cells.y(n) - Cells.y(cur)
                    val cross = dirX * ny - dirY * nx
                    if (cross > 0) 0 else if (cross == 0) 1 else 2
                }!!
                nexts.remove(next)
                if (nexts.isEmpty()) out.remove(cur)
                val nx = Cells.x(next) - Cells.x(cur)
                val ny = Cells.y(next) - Cells.y(cur)
                if (nx != dirX || ny != dirY) pts += Cells.x(cur) to Cells.y(cur)
                dirX = nx; dirY = ny
                cur = next
            } while (cur != start && ++guard < 1_000_000)
            if (pts.size >= 3) {
                pts += pts.first()
                var area = 0L
                for (i in 0 until pts.size - 1) area += pts[i].first.toLong() * pts[i + 1].second - pts[i + 1].first.toLong() * pts[i].second
                loops += pts to if (area > 0) 1 else -1
            }
        }
        return loops
    }

    /** Uncovered area in square kilometres. */
    fun areaKm2(): Double {
        val lat = prefs.lastFix?.first ?: 52.0
        val m = Cells.metersAcross(lat)
        return _cellCount.value * m * m / 1_000_000
    }

    // ---------------------------------------------------------------- storage

    private fun load() {
        runCatching {
            if (cellsFile.exists()) DataInputStream(cellsFile.inputStream().buffered()).use { s ->
                while (s.available() >= 8) cells.add(s.readLong())
            }
        }
        runCatching {
            if (!stateFile.exists()) return@runCatching
            val o = JSONObject(stateFile.readText())
            _distanceKm.value = o.optDouble("km", 0.0)
            val arr = o.optJSONArray("cities") ?: JSONArray()
            _cities.value = (0 until arr.length()).map {
                val c = arr.getJSONObject(it)
                City(c.getString("n"), c.optString("c"), c.optLong("t"), c.optDouble("la"), c.optDouble("lo"))
            }
            o.optJSONObject("fix")?.let { f ->
                lastFix = Location("saved").apply { latitude = f.getDouble("la"); longitude = f.getDouble("lo"); time = f.getLong("t") }
            }
        }
        _cellCount.value = cells.size
    }

    private fun appendCells(keys: List<Long>) {
        runCatching {
            DataOutputStream(java.io.FileOutputStream(cellsFile, true).buffered()).use { s -> keys.forEach { s.writeLong(it) } }
        }
    }

    private fun save() {
        runCatching {
            val o = JSONObject()
                .put("km", _distanceKm.value)
                .put("cities", JSONArray().apply {
                    _cities.value.forEach { c ->
                        put(JSONObject().put("n", c.name).put("c", c.country).put("t", c.firstVisit).put("la", c.lat).put("lo", c.lon))
                    }
                })
            lastFix?.let { o.put("fix", JSONObject().put("la", it.latitude).put("lo", it.longitude).put("t", it.time)) }
            stateFile.writeText(o.toString())
        }
    }

    fun erase() {
        synchronized(cells) { cells.clear() }
        cellsFile.delete()
        stateFile.delete()
        _cities.value = emptyList()
        _distanceKm.value = 0.0
        _cellCount.value = 0
        lastFix = null
        _version.value++
    }

    internal fun setExpedition(on: Boolean) { _expedition.value = on }

    // ---------------------------------------------------------------- notifications

    private fun notifyCity(city: City) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context)
        val tap = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            city.name.hashCode(),
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Area unlocked: ${city.name}")
                .setContentText("ISAC: New area discovered. ${city.name} added to your intel.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build(),
        )
    }
}

internal const val CHANNEL = "isac"

internal fun ensureChannel(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    if (nm.getNotificationChannel(CHANNEL) == null) {
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "ISAC", NotificationManager.IMPORTANCE_LOW))
    }
}

/** Whether the launcher is on screen; set by the activity. Used to decide between a pop-up and a notification. */
object Graphless {
    @Volatile var isUiVisible = false
}

// -------------------------------------------------------------------- location sources

/** Keeps location fixes flowing into the exploration record while the user allows it. */
object ExplorationTracker {
    fun hasLocation(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun hasBackground(context: Context) = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, LocationReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    /**
     * Listens to location the cheap way: "passive" fixes that other apps already ask for (navigation,
     * weather), plus a low-power fix every few minutes. Android only delivers these in the background
     * when location is allowed all the time.
     */
    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        val mode = context.graph.prefs.tracking
        if (mode == Tracking.OFF || !hasLocation(context)) { stop(context); return }
        val lm = context.getSystemService(LocationManager::class.java)
        val pi = pendingIntent(context)
        runCatching { lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 20_000L, 25f, pi) }
        val active = if (Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER)) LocationManager.FUSED_PROVIDER
        else LocationManager.NETWORK_PROVIDER
        runCatching { lm.requestLocationUpdates(active, 4 * 60_000L, 40f, pi) }
    }

    fun stop(context: Context) {
        runCatching { context.getSystemService(LocationManager::class.java).removeUpdates(pendingIntent(context)) }
    }
}

class LocationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.graph
        if (graph.prefs.tracking == Tracking.WHILE_OPEN && !Graphless.isUiVisible) return
        @Suppress("DEPRECATION")
        val single = intent.getParcelableExtra<Location>(LocationManager.KEY_LOCATION_CHANGED)
        val many = if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Location>(LocationManager.KEY_LOCATIONS)
        } else null
        (many ?: listOfNotNull(single)).sortedBy { it.time }.forEach { graph.exploration.record(it) }
    }
}

/** Restarts tracking after the phone reboots (location requests don't survive a restart). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ExplorationTracker.start(context)
    }
}

/**
 * Expedition mode: precise GPS every few seconds for a trip, with a notification while it runs,
 * so the map draws the whole route instead of the occasional point.
 */
class ExpeditionService : Service() {
    private var startKm = 0.0
    private var startCells = 0
    private val listener = LocationListener { loc ->
        val ex = graph.exploration
        ex.record(loc)
        notifyProgress()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val ex = graph.exploration
        startKm = ex.distanceKm.value
        startCells = ex.cellCount.value
        ensureChannel(this)
        val note = buildNote("Starting…")
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTE_ID, note)
        ex.setExpedition(true)
        val lm = getSystemService(LocationManager::class.java)
        if (!ExplorationTracker.hasLocation(this)) { stopSelf(); return START_NOT_STICKY }
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4_000L, 10f, listener, Looper.getMainLooper()) }
        runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 15_000L, 25f, listener, Looper.getMainLooper()) }
        return START_STICKY
    }

    private fun notifyProgress() {
        val ex = graph.exploration
        val km = ex.distanceKm.value - startKm
        val newCells = ex.cellCount.value - startCells
        getSystemService(NotificationManager::class.java).notify(NOTE_ID, buildNote("%.1f km · %d new cells".format(Locale.UK, km, newCells)))
    }

    private fun buildNote(text: String): Notification {
        val stop = PendingIntent.getService(this, 2, Intent(this, ExpeditionService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ISAC · Expedition in progress")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "End expedition", stop).build())
            .build()
    }

    override fun onDestroy() {
        runCatching { getSystemService(LocationManager::class.java).removeUpdates(listener) }
        graph.exploration.setExpedition(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.isac.launcher.STOP_EXPEDITION"
        private const val NOTE_ID = 0x15AC

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, ExpeditionService::class.java))
        fun stop(context: Context) = context.startService(Intent(context, ExpeditionService::class.java).setAction(ACTION_STOP))
    }
}
