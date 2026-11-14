package app.isac.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class Weather(
    val tempC: Int,
    val code: Int,
    val isDay: Boolean,
    val highC: Int,
    val lowC: Int,
    val place: String,
    val fetchedAt: Long,
) {
    val description: String get() = describe(code)

    fun toJson(): String = JSONObject()
        .put("t", tempC).put("c", code).put("d", isDay).put("h", highC).put("l", lowC)
        .put("p", place).put("f", fetchedAt).toString()

    companion object {
        fun fromJson(s: String?): Weather? = runCatching {
            val o = JSONObject(s ?: return null)
            Weather(o.getInt("t"), o.getInt("c"), o.getBoolean("d"), o.getInt("h"), o.getInt("l"),
                o.optString("p"), o.getLong("f"))
        }.getOrNull()

        /** WMO weather interpretation codes, as used by Open-Meteo. */
        fun describe(code: Int): String = when (code) {
            0 -> "Clear"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67 -> "Rain"
            71, 73, 75, 77 -> "Snow"
            80, 81, 82 -> "Showers"
            85, 86 -> "Snow showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Unknown"
        }
    }
}

enum class WeatherProblem { NEED_LOCATION, NO_LOCATION_FIX, TOWN_NOT_FOUND, OFFLINE }

class WeatherRepository(private val context: Context, private val prefs: Prefs) {
    private val _weather = MutableStateFlow(Weather.fromJson(prefs.weatherCache))
    val weather: StateFlow<Weather?> = _weather

    private val _problem = MutableStateFlow<WeatherProblem?>(null)
    val problem: StateFlow<WeatherProblem?> = _problem

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(force: Boolean = false) {
        val cached = _weather.value
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < 30 * 60_000) return
        val town = prefs.settings.value.weatherTown.trim()
        val place: Triple<Double, Double, String> = if (town.isNotEmpty()) {
            geocode(town) ?: run { _problem.value = WeatherProblem.TOWN_NOT_FOUND; return }
        } else {
            if (!hasLocationPermission()) { _problem.value = WeatherProblem.NEED_LOCATION; return }
            val loc = currentLocation() ?: run { _problem.value = WeatherProblem.NO_LOCATION_FIX; return }
            Triple(loc.latitude, loc.longitude, placeName(loc))
        }
        prefs.lastFix = place.first to place.second
        val w = fetch(place.first, place.second, place.third)
        if (w == null) {
            _problem.value = WeatherProblem.OFFLINE
            return
        }
        _problem.value = null
        _weather.value = w
        prefs.weatherCache = w.toJson()
    }

    private suspend fun fetch(lat: Double, lon: Double, place: String): Weather? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.US, lat, lon) +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"
        val body = get(url) ?: return@withContext null
        runCatching {
            val o = JSONObject(body)
            val cur = o.getJSONObject("current")
            val daily = o.getJSONObject("daily")
            Weather(
                tempC = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                highC = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                lowC = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                place = place,
                fetchedAt = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    private suspend fun geocode(town: String): Triple<Double, Double, String>? = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
            URLEncoder.encode(town, "UTF-8")
        val body = get(url) ?: return@withContext null
        runCatching {
            val r = JSONObject(body).getJSONArray("results").getJSONObject(0)
            Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name"))
        }.getOrNull()
    }

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "IsacLauncher/0.1")
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    /** Where the phone is now, or null without location access or a fix. */
    suspend fun locate(): Location? = if (hasLocationPermission()) currentLocation() else null

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) || it == LocationManager.PASSIVE_PROVIDER }.getOrDefault(false) }

        val gpsOn = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val last = (providers + if (gpsOn) listOf(LocationManager.GPS_PROVIDER) else emptyList())
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 3_600_000) return last

        if (Build.VERSION.SDK_INT >= 30) {
            // Ask the low-power provider first, then GPS if that comes back empty.
            val candidates = providers.filter { it != LocationManager.PASSIVE_PROVIDER }.take(1) +
                if (gpsOn) listOf(LocationManager.GPS_PROVIDER) else emptyList()
            for (provider in candidates) {
                val fresh = withTimeoutOrNull(8_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        runCatching {
                            lm.getCurrentLocation(provider, null, context.mainExecutor) { loc -> cont.resume(loc) }
                        }.onFailure { cont.resume(null) }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return last
    }

    @Suppress("DEPRECATION")
    private suspend fun placeName(loc: Location): String = withContext(Dispatchers.IO) {
        runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: ""
    }
}
