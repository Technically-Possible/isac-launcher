package app.isac.launcher.data

import kotlin.math.PI
import kotlin.math.cos

/** Moon phase worked out from the date, so it needs no network. Accurate to within about half a day. */
object Moon {
    private const val SYNODIC = 29.530588853
    private const val DAY_MS = 86_400_000.0
    /** A known new moon: 2000-01-06 18:14 UTC. */
    private const val REF_NEW_MOON = 947_182_440_000L

    data class Phase(
        val ageDays: Double,
        /** 0.0 (new) to 1.0 (full). */
        val illumination: Double,
        val waxing: Boolean,
        val name: String,
        val nextNew: Long,
        val nextFull: Long,
    )

    fun at(time: Long): Phase {
        val days = (time - REF_NEW_MOON) / DAY_MS
        val age = ((days % SYNODIC) + SYNODIC) % SYNODIC
        val illum = (1 - cos(2 * PI * age / SYNODIC)) / 2
        val name = when {
            age < 1.0 -> "New moon"
            age < 6.4 -> "Waxing crescent"
            age < 8.4 -> "First quarter"
            age < 13.8 -> "Waxing gibbous"
            age < 15.8 -> "Full moon"
            age < 21.1 -> "Waning gibbous"
            age < 23.1 -> "Last quarter"
            age < 28.5 -> "Waning crescent"
            else -> "New moon"
        }
        val half = SYNODIC / 2
        val toFull = if (age < half) half - age else SYNODIC + half - age
        return Phase(
            ageDays = age,
            illumination = illum,
            waxing = age < half,
            name = name,
            nextNew = time + ((SYNODIC - age) * DAY_MS).toLong(),
            nextFull = time + (toFull * DAY_MS).toLong(),
        )
    }
}
