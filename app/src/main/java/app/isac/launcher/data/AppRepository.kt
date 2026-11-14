package app.isac.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Category(val label: String) {
    COMMS("COMMS"), MEDIA("MEDIA"), TOOLS("TOOLS"), GAMES("GAMES"), OTHER("")
}

data class AppEntry(
    /** Stable id: "package/activity#userSerial". */
    val key: String,
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWork: Boolean,
    val category: Category,
    val firstInstall: Long,
    val info: LauncherActivityInfo,
)

class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _installed = MutableSharedFlow<AppEntry>(extraBufferCapacity = 4)
    /** Emits apps that were just installed, for the "new item" pop-up. */
    val installed: SharedFlow<AppEntry> = _installed

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            scope.launch {
                refresh()
                _apps.value.firstOrNull { it.pkg == packageName && it.user == user }?.let { _installed.tryEmit(it) }
            }
        }
        override fun onPackageRemoved(packageName: String, user: UserHandle) { scope.launch { refresh() } }
        override fun onPackageChanged(packageName: String, user: UserHandle) { scope.launch { refresh() } }
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            scope.launch { refresh() }
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            scope.launch { refresh() }
        }
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    suspend fun refresh() {
        val list = withContext(Dispatchers.IO) {
            val me = Process.myUserHandle()
            userManager.userProfiles.flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                    .filter { it.componentName.packageName != context.packageName }
                    .map { info ->
                        AppEntry(
                            key = "${info.componentName.flattenToShortString()}#$serial",
                            label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                            pkg = info.componentName.packageName,
                            component = info.componentName,
                            user = user,
                            isWork = user != me,
                            category = categoryOf(info.applicationInfo),
                            firstInstall = info.firstInstallTime,
                            info = info,
                        )
                    }
            }.sortedBy { it.label.lowercase() }
        }
        _apps.value = list
    }

    fun launch(entry: AppEntry, bounds: Rect? = null) {
        runCatching { launcherApps.startMainActivity(entry.component, entry.user, bounds, null) }
    }

    fun openAppInfo(entry: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null) }
    }

    fun uninstall(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.pkg, null))
            .putExtra(Intent.EXTRA_USER, entry.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun categoryOf(ai: ApplicationInfo): Category {
        val byDeclared = when (ai.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> Category.COMMS
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> Category.MEDIA
            ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_PRODUCTIVITY -> Category.TOOLS
            ApplicationInfo.CATEGORY_GAME -> Category.GAMES
            else -> null
        }
        if (byDeclared != null) return byDeclared
        // Many apps don't declare a category, so fall back to a few well-known package names.
        val p = ai.packageName.lowercase()
        return when {
            COMMS_HINTS.any { it in p } -> Category.COMMS
            MEDIA_HINTS.any { it in p } -> Category.MEDIA
            TOOLS_HINTS.any { it in p } -> Category.TOOLS
            else -> Category.OTHER
        }
    }

    private companion object {
        val COMMS_HINTS = listOf("dialer", "messag", "mms", "mail", "android.gm", "contacts", "whatsapp", "telegram",
            "signal", "discord", "slack", "snapchat", "reddit", "instagram", "facebook", "twitter", "teams", "revenge")
        val MEDIA_HINTS = listOf("camera", "photos", "gallery", "music", "youtube", "spotify", "tiktok",
            "netflix", "video", "podcast", "audio")
        val TOOLS_HINTS = listOf("calendar", "calculator", "clock", "deskclock", "files", "documents", "maps",
            "notes", "keep", "drive", "docs", "settings", "monzo", "bank", "wallet", "weather", "watch")
    }
}
