package app.isac.launcher.data

/** Accent colours, unlocked by levelling up. */
enum class Accent(val label: String, val argb: Long, val level: Int) {
    ORANGE("SHD ORANGE", 0xFFFF6D10, 1),
    AMBER("SIGNAL AMBER", 0xFFFFB020, 3),
    CYAN("TECH CYAN", 0xFF2FC6E6, 5),
    GREEN("FIELD GREEN", 0xFF7BD35C, 8),
    VIOLET("ECHO VIOLET", 0xFFA070FF, 12),
    GOLD("HIGH-END GOLD", 0xFFE8B923, 16);

    val hex: String get() = "#%06X".format(argb and 0xFFFFFF)
}

/** City skies, unlocked by levelling up. LIVE follows the real sky and weather. */
enum class Sky(val label: String, val level: Int) {
    LIVE("LIVE", 1),
    DUSK("DUSK", 1),
    NIGHT("NIGHT OPS", 4),
    WINTER("WINTER", 7),
    DARK_ZONE("DARK ZONE", 10),
}

object Rewards {
    fun accents(level: Int) = Accent.entries.filter { it.level <= level }
    fun skies(level: Int) = Sky.entries.filter { it.level <= level }

    /** Everything that unlocks exactly at [level], for the level-up banner. */
    fun unlockedAt(level: Int): List<String> =
        Accent.entries.filter { it.level == level }.map { "${it.label.lowercase().replaceFirstChar(Char::uppercase)} accent" } +
            Sky.entries.filter { it.level == level && level > 1 }.map { "${it.label.lowercase().replaceFirstChar(Char::uppercase)} sky" }

    fun nextUnlock(level: Int): String? {
        val a = Accent.entries.firstOrNull { it.level > level }
        val s = Sky.entries.firstOrNull { it.level > level }
        val next = listOfNotNull(a?.let { it.level to "${it.label} accent" }, s?.let { it.level to "${it.label} sky" }).minByOrNull { it.first }
        return next?.let { "${it.second} at level ${it.first}" }
    }
}
