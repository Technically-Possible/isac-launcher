package app.isac.launcher.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

enum class Rarity(val label: String) {
    EXOTIC("EXOTIC"), HIGH_END("HIGH-END"), SUPERIOR("SUPERIOR"), SPECIALIZED("SPECIALIZED"),
    STANDARD("STANDARD"), WORN("WORN"), GEAR_SET("GEAR SET")
}

data class Usage(
    val foreground7d: Long = 0,
    val launches7d: Int = 0,
    val daysUsed7d: Int = 0,
    val lastUsed: Long = 0,
)

data class UsageSnapshot(
    val byPackage: Map<String, Usage> = emptyMap(),
    /** The three packages with the most screen time in the last 7 days. */
    val top3: Set<String> = emptySet(),
    val maxLaunches: Int = 1,
    val hasAccess: Boolean = false,
)

class UsageRepository(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    private val _snapshot = MutableStateFlow(UsageSnapshot())
    val snapshot: StateFlow<UsageSnapshot> = _snapshot

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (!hasAccess()) {
            _snapshot.value = UsageSnapshot(hasAccess = false)
            return@withContext
        }
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val result = HashMap<String, Usage>()

        usm.queryAndAggregateUsageStats(now - 90 * day, now).forEach { (pkg, s) ->
            result[pkg] = Usage(lastUsed = s.lastTimeUsed)
        }
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7 * day, now)
            .filter { it.totalTimeInForeground > 0 && it.lastTimeUsed >= now - 7 * day }
            .forEach { s ->
                val u = result[s.packageName] ?: Usage()
                result[s.packageName] = u.copy(
                    foreground7d = u.foreground7d + s.totalTimeInForeground,
                    daysUsed7d = u.daysUsed7d + 1,
                    lastUsed = maxOf(u.lastUsed, s.lastTimeUsed),
                )
            }

        // A launch is a switch into an app from a different one.
        val events = usm.queryEvents(now - 7 * day, now)
        val e = UsageEvents.Event()
        var last: String? = null
        val launches = HashMap<String, Int>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (e.packageName != last) launches[e.packageName] = (launches[e.packageName] ?: 0) + 1
                last = e.packageName
            }
        }
        launches.forEach { (pkg, n) -> result[pkg] = (result[pkg] ?: Usage()).copy(launches7d = n) }

        val ignore = setOf(context.packageName, "com.android.systemui")
        val top3 = result.filterKeys { it !in ignore && !it.contains("launcher") }
            .entries.sortedByDescending { it.value.foreground7d }
            .filter { it.value.foreground7d > 0 }
            .take(3).map { it.key }.toSet()

        _snapshot.value = UsageSnapshot(
            byPackage = result,
            top3 = top3,
            maxLaunches = (result.values.maxOfOrNull { it.launches7d } ?: 1).coerceAtLeast(1),
            hasAccess = true,
        )
    }

    fun rarityOf(entry: AppEntry, snap: UsageSnapshot, byUsage: Boolean): Rarity {
        if (entry.isWork) return Rarity.GEAR_SET
        if (!byUsage || !snap.hasAccess) return Rarity.STANDARD
        val u = snap.byPackage[entry.pkg] ?: return Rarity.WORN
        val age = System.currentTimeMillis() - u.lastUsed
        val day = 86_400_000L
        return when {
            entry.pkg in snap.top3 -> Rarity.EXOTIC
            u.daysUsed7d >= 4 -> Rarity.HIGH_END
            u.lastUsed > 0 && age <= 7 * day -> Rarity.SUPERIOR
            u.lastUsed > 0 && age <= 30 * day -> Rarity.SPECIALIZED
            u.lastUsed > 0 && age <= 90 * day -> Rarity.STANDARD
            else -> Rarity.WORN
        }
    }

    /** App + data size in bytes, or null without usage access. */
    suspend fun storageBytes(entry: AppEntry): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val ssm = context.getSystemService(StorageStatsManager::class.java)
            val st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, entry.pkg, entry.user)
            st.appBytes + st.dataBytes
        }.getOrNull()
    }
}
