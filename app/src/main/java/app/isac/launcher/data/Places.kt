package app.isac.launcher.data

import android.content.Context
import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.floor

/**
 * A place from OpenStreetMap. [landmark] places are collectible caches on the map; [access] is the
 * place's wheelchair tag ("yes", "limited" or "designated"); [toilet] marks an accessible toilet.
 */
data class Place(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val kind: String,
    val landmark: Boolean,
    val access: String?,
    val toilet: Boolean,
)

/**
 * Landmarks and accessible places around wherever you go, fetched from OpenStreetMap's Overpass
 * service one area at a time (about 3 km across) and kept on the phone for a month.
 */
class PlacesRepository(private val context: Context, private val exploration: Exploration, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "places.json")
    private val tiles = HashMap<String, Long>()
    private val byId = LinkedHashMap<Long, Place>()
    private val lock = Mutex()
    private var lastRequest = 0L

    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places: StateFlow<List<Place>> = _places

    init { load() }

    private fun tileKey(lat: Double, lon: Double) = "${floor(lat / STEP).toInt()}:${floor(lon / STEP).toInt()}"

    /** Called for each precise location fix: recovers caches in reach and fetches new areas. */
    fun onFix(loc: Location) {
        checkNearby(loc)
        scope.launch { ensureAround(loc.latitude, loc.longitude) }
    }

    private fun checkNearby(loc: Location) {
        val out = FloatArray(1)
        val found = exploration.caches.value
        for (p in byId.values) {
            if (!p.landmark || p.id in found) continue
            Location.distanceBetween(loc.latitude, loc.longitude, p.lat, p.lon, out)
            if (out[0] <= REACH_M) exploration.recoverCache(p)
        }
    }

    /** Makes sure the area around a point has been fetched. At most one request a minute. */
    suspend fun ensureAround(lat: Double, lon: Double) = lock.withLock {
        val key = tileKey(lat, lon)
        val fetched = tiles[key]
        val now = System.currentTimeMillis()
        if (fetched != null && now - fetched < 30L * 86_400_000) return@withLock
        if (now - lastRequest < 60_000) return@withLock
        lastRequest = now
        val s = floor(lat / STEP) * STEP
        val w = floor(lon / STEP) * STEP
        val fresh = withContext(Dispatchers.IO) { fetch(s, w, s + STEP, w + STEP) } ?: return@withLock
        fresh.forEach { byId[it.id] = it }
        tiles[key] = now
        _places.value = byId.values.toList()
        withContext(Dispatchers.IO) { save() }
    }

    private fun fetch(s: Double, w: Double, n: Double, e: Double): List<Place>? {
        val bbox = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", s, w, n, e)
        val access = "^(yes|limited|designated)$"
        val query = """
            [out:json][timeout:25];
            (
              nwr["historic"~"^(memorial|monument|castle|ruins|archaeological_site|wayside_cross|wayside_shrine|boundary_stone|milestone|manor|city_gate|tower|mine|aircraft|tank|cannon|church)$"]["name"]($bbox);
              nwr["tourism"~"^(viewpoint|artwork|museum|attraction|gallery)$"]["name"]($bbox);
              nwr["wheelchair"~"$access"]["name"]($bbox);
              nwr["amenity"="toilets"]["wheelchair"~"$access"]($bbox);
              nwr["amenity"="toilets"]["toilets:wheelchair"~"$access"]($bbox);
            );
            out center tags 800;
        """.trimIndent()
        val body = runCatching {
            val c = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 40_000
            c.setRequestProperty("User-Agent", "IsacLauncher/0.2")
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            try {
                if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
            } finally { c.disconnect() }
        }.getOrNull() ?: return null
        return runCatching {
            val els = JSONObject(body).getJSONArray("elements")
            (0 until els.length()).mapNotNull { i -> parse(els.getJSONObject(i)) }
        }.getOrNull()
    }

    private fun parse(o: JSONObject): Place? {
        val tags = o.optJSONObject("tags") ?: return null
        val type = o.optString("type")
        val lat = if (o.has("lat")) o.getDouble("lat") else o.optJSONObject("center")?.optDouble("lat") ?: return null
        val lon = if (o.has("lon")) o.getDouble("lon") else o.optJSONObject("center")?.optDouble("lon") ?: return null
        val id = o.getLong("id") * 4 + when (type) { "node" -> 0; "way" -> 1; else -> 2 }
        val historic = tags.optString("historic").takeIf { it.isNotEmpty() }
        val tourism = tags.optString("tourism").takeIf { it.isNotEmpty() }
        val amenity = tags.optString("amenity").takeIf { it.isNotEmpty() }
        val wheel = tags.optString("wheelchair").takeIf { it in ACCESS }
        val toiletWheel = tags.optString("toilets:wheelchair").takeIf { it in ACCESS }
        val toilet = amenity == "toilets" && (wheel != null || toiletWheel != null)
        val landmark = tags.has("name") && (historic != null ||
            tourism in setOf("viewpoint", "artwork", "museum", "attraction", "gallery"))
        val kind = (historic ?: tourism ?: amenity ?: tags.optString("shop").ifEmpty { "place" }).replace('_', ' ')
        val name = tags.optString("name").ifEmpty { if (toilet) "Accessible toilet" else return null }
        if (!landmark && wheel == null && !toilet) return null
        return Place(id, name, lat, lon, kind, landmark, wheel ?: toiletWheel, toilet)
    }

    // ---- GeoJSON for the map ----

    fun cachesGeoJson(found: Set<Long>): String = featureCollection(byId.values.filter { it.landmark }) { p ->
        JSONObject().put("name", p.name.uppercase()).put("found", p.id in found).put("kind", p.kind)
    }

    fun accessGeoJson(): String = featureCollection(byId.values.filter { it.access != null || it.toilet }) { p ->
        JSONObject().put("name", p.name).put("access", p.access ?: "yes").put("toilet", p.toilet).put("kind", p.kind)
    }

    private fun featureCollection(list: Collection<Place>, props: (Place) -> JSONObject): String {
        val feats = JSONArray()
        list.forEach { p ->
            feats.put(
                JSONObject().put("type", "Feature").put("id", p.id).put("properties", props(p).put("pid", p.id))
                    .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(p.lon).put(p.lat))),
            )
        }
        return JSONObject().put("type", "FeatureCollection").put("features", feats).toString()
    }

    fun get(id: Long): Place? = byId[id]

    // ---- storage ----

    private fun load() {
        runCatching {
            if (!file.exists()) return@runCatching
            val o = JSONObject(file.readText())
            o.optJSONObject("tiles")?.let { t -> t.keys().forEach { tiles[it] = t.getLong(it) } }
            val arr = o.optJSONArray("places") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val place = Place(
                    p.getLong("id"), p.getString("n"), p.getDouble("la"), p.getDouble("lo"), p.optString("k"),
                    p.optBoolean("lm"), p.optString("a").ifEmpty { null }, p.optBoolean("t"),
                )
                byId[place.id] = place
            }
            _places.value = byId.values.toList()
        }
    }

    private fun save() {
        runCatching {
            val arr = JSONArray()
            byId.values.forEach { p ->
                arr.put(JSONObject().put("id", p.id).put("n", p.name).put("la", p.lat).put("lo", p.lon).put("k", p.kind)
                    .put("lm", p.landmark).put("a", p.access ?: "").put("t", p.toilet))
            }
            val t = JSONObject().apply { tiles.forEach { (k, v) -> put(k, v) } }
            file.writeText(JSONObject().put("tiles", t).put("places", arr).toString())
        }
    }

    private companion object {
        const val STEP = 0.03
        const val REACH_M = 60f
        val ACCESS = setOf("yes", "limited", "designated")
    }
}
