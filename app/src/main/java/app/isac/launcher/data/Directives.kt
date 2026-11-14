package app.isac.launcher.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import kotlin.random.Random

enum class DirectiveKind(val target: Int, val text: String, val short: String) {
    EXPLORE(15, "Uncover 15 new map cells", "EXPLORE"),
    TRAVEL(3, "Travel 3 km", "TRAVEL KM"),
    CACHE(1, "Recover a landmark cache", "CACHE"),
    PULSE(1, "Run a Pulse scan", "PULSE"),
    INBOX(1, "Clear all your notifications", "INBOX ZERO"),
    VOICE(1, "Give ISAC a voice command", "VOICE"),
    AWAY(1, "Head out 1 km from base", "HEAD OUT"),
}

data class Directive(val kind: DirectiveKind, val progress: Int, val done: Boolean) {
    val text get() = kind.text
    val target get() = kind.target
}

/**
 * Three small goals a day, worth 50 XP each. Picked from a pool each morning; goals that need
 * something you haven't set up (a base, notification access) are left out.
 */
class Directives(private val prefs: Prefs, private val exploration: Exploration) {
    private val _today = MutableStateFlow<List<Directive>>(emptyList())
    val today: StateFlow<List<Directive>> = _today

    private val _completed = MutableSharedFlow<Directive>(extraBufferCapacity = 4)
    val completed: SharedFlow<Directive> = _completed

    private fun day() = LocalDate.now().toEpochDay()

    /** Starts a new set of directives when the day changes. */
    private fun rollIfNeeded(badgeAccess: Boolean) {
        val d = day()
        if (prefs.getLong("dir_day", -1) == d) return
        prefs.putLong("dir_day", d)
        prefs.putLong("dir_cells", exploration.cellCount.value.toLong())
        prefs.putLong("dir_m", (exploration.distanceKm.value * 1000).toLong())
        prefs.putLong("dir_caches", exploration.caches.value.size.toLong())
        listOf("dir_pulse", "dir_voice", "dir_inbox", "dir_away").forEach { prefs.putLong(it, 0) }
        prefs.putString("dir_done", "")
        val pool = DirectiveKind.entries.filter {
            when (it) {
                DirectiveKind.INBOX -> badgeAccess
                DirectiveKind.AWAY -> prefs.base.value != null
                else -> true
            }
        }
        val picked = pool.shuffled(Random(d)).take(3)
        prefs.putString("dir_kinds", picked.joinToString(",") { it.name })
    }

    private fun progress(k: DirectiveKind): Int = when (k) {
        DirectiveKind.EXPLORE -> (exploration.cellCount.value - prefs.getLong("dir_cells")).toInt()
        DirectiveKind.TRAVEL -> ((exploration.distanceKm.value * 1000 - prefs.getLong("dir_m")) / 1000).toInt()
        DirectiveKind.CACHE -> (exploration.caches.value.size - prefs.getLong("dir_caches")).toInt()
        DirectiveKind.PULSE -> prefs.getLong("dir_pulse").toInt()
        DirectiveKind.VOICE -> prefs.getLong("dir_voice").toInt()
        DirectiveKind.INBOX -> prefs.getLong("dir_inbox").toInt()
        DirectiveKind.AWAY -> prefs.getLong("dir_away").toInt()
    }.coerceAtLeast(0)

    /** Recomputes progress and awards any directive that has just been completed. */
    fun refresh(badgeAccess: Boolean = false) {
        rollIfNeeded(badgeAccess)
        val kinds = (prefs.getString("dir_kinds") ?: "").split(',').mapNotNull { n -> DirectiveKind.entries.firstOrNull { it.name == n } }
        val done = (prefs.getString("dir_done") ?: "").split(',').filter { it.isNotBlank() }.toMutableSet()
        val list = kinds.map { k ->
            val p = progress(k).coerceAtMost(k.target)
            val isDone = k.name in done
            if (!isDone && p >= k.target) {
                done += k.name
                prefs.putString("dir_done", done.joinToString(","))
                val d = Directive(k, p, true)
                _completed.tryEmit(d)
                exploration.reward(XP)
                d
            } else Directive(k, p, isDone)
        }
        _today.value = list
    }

    fun notePulse() { prefs.putLong("dir_pulse", prefs.getLong("dir_pulse") + 1); refresh() }
    fun noteVoice() { prefs.putLong("dir_voice", prefs.getLong("dir_voice") + 1); refresh() }

    /** Counts as clearing your notifications when the total drops to zero after there were some. */
    fun noteBadges(total: Int, previous: Int) {
        if (total == 0 && previous > 0) { prefs.putLong("dir_inbox", 1); refresh(true) }
    }

    fun noteDistanceFromBase(meters: Float) {
        if (meters >= 1_000f && prefs.getLong("dir_away") == 0L) { prefs.putLong("dir_away", 1); refresh() }
    }

    companion object { const val XP = 50 }
}
