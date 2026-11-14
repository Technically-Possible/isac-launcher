package app.isac.launcher.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Unread notification counts per app, for the orange counters on app tiles. */
object Badges {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Keyed by [key]. */
    val counts: StateFlow<Map<String, Int>> = _counts

    fun key(pkg: String, user: UserHandle) = "$pkg|${user.hashCode()}"

    fun countFor(counts: Map<String, Int>, entry: AppEntry): Int = counts[key(entry.pkg, entry.user)] ?: 0

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun component(context: Context) = ComponentName(context, BadgeListener::class.java)

    internal fun publish(map: Map<String, Int>) {
        _counts.value = map
    }
}

/**
 * Counts each app's notifications, the same way Android's own launcher does: ongoing notifications
 * (music, navigation, downloads) and group summaries are skipped, and an app's own "number" is used
 * when it sets one. Only the counts are kept; notification content is never read or stored.
 */
class BadgeListener : NotificationListenerService() {
    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() = Badges.publish(emptyMap())
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val map = HashMap<String, Int>()
        for (sbn in active) {
            val n = sbn.notification ?: continue
            if (sbn.isOngoing) continue
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) continue
            if (sbn.packageName == packageName) continue
            val k = Badges.key(sbn.packageName, sbn.user)
            map[k] = (map[k] ?: 0) + if (n.number > 0) n.number else 1
        }
        Badges.publish(map)
    }
}
