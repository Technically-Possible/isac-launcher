package app.isac.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalEvent(val title: String, val begin: Long, val allDay: Boolean)

class CalendarRepository(private val context: Context) {
    fun hasAccess() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    /** Days of [month] (1-based) in [year] that have at least one event. */
    suspend fun eventDays(year: Int, month: Int): Set<Int> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptySet()
        val zone = ZoneId.systemDefault()
        val first = LocalDate.of(year, month, 1)
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val days = HashSet<Int>()
        query(start, end) { e ->
            // All-day events are stored at UTC midnight.
            val d = Instant.ofEpochMilli(e.begin).atZone(if (e.allDay) ZoneOffset.UTC else zone).toLocalDate()
            if (d.year == year && d.monthValue == month) days += d.dayOfMonth
        }
        days
    }

    /** The next event starting within 7 days. */
    suspend fun nextEvent(): CalEvent? = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext null
        val now = System.currentTimeMillis()
        var best: CalEvent? = null
        query(now, now + 7 * 86_400_000L) { e ->
            if (!e.allDay && e.begin >= now && (best == null || e.begin < best!!.begin)) best = e
        }
        best
    }

    private fun query(start: Long, end: Long, each: (CalEvent) -> Unit) {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    each(CalEvent(c.getString(0) ?: "Event", c.getLong(1), c.getInt(2) == 1))
                }
            }
        }
    }
}
