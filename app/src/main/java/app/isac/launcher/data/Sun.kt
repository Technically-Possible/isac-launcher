package app.isac.launcher.data

import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

enum class SkyPhase { NIGHT, DAWN, DAY, DUSK }

/** Sunrise and sunset from the standard sunrise equation. Good to a minute or two. */
object Sun {
    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI

    /** Sunrise and sunset in epoch millis, or null during polar day or night. */
    fun times(date: LocalDate, lat: Double, lon: Double): Pair<Long, Long>? {
        val jd = date.toEpochDay() + 2440587.5 + 0.5
        val n = ceil(jd - 2451545.0 + 0.0008)
        val jStar = n - lon / 360.0
        val m = (357.5291 + 0.98560028 * jStar) % 360
        val c = 1.9148 * sin(rad(m)) + 0.0200 * sin(rad(2 * m)) + 0.0003 * sin(rad(3 * m))
        val lambda = (m + c + 180 + 102.9372) % 360
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(rad(m)) - 0.0069 * sin(rad(2 * lambda))
        val decl = asin(sin(rad(lambda)) * sin(rad(23.44)))
        val cosW = (sin(rad(-0.83)) - sin(rad(lat)) * sin(decl)) / (cos(rad(lat)) * cos(decl))
        if (cosW < -1 || cosW > 1) return null
        val w = deg(acos(cosW))
        fun toMillis(j: Double) = ((j - 2440587.5) * 86_400_000).toLong()
        return toMillis(jTransit - w / 360) to toMillis(jTransit + w / 360)
    }

    /** Which sky to draw: dawn and dusk run 45 minutes either side of sunrise and sunset. */
    fun phase(now: Long, lat: Double?, lon: Double?): SkyPhase {
        if (lat == null || lon == null) return SkyPhase.DUSK
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val (rise, set) = times(today, lat, lon) ?: return if (lat * (today.monthValue - 6.5) < 0) SkyPhase.DAY else SkyPhase.NIGHT
        val window = 45 * 60_000L
        return when {
            now < rise - window || now > set + window -> SkyPhase.NIGHT
            now <= rise + window -> SkyPhase.DAWN
            now >= set - window -> SkyPhase.DUSK
            else -> SkyPhase.DAY
        }
    }
}
