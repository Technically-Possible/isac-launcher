package app.isac.launcher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
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
    /** In Android's private space (Android 15+). Kept out of the normal lists. */
    val isPrivate: Boolean,
    val category: Category,
    val firstInstall: Long,
    val info: LauncherActivityInfo,
)

/** The private space profile, if the phone has one and ISAC is allowed to see it. */
data class PrivateSpace(val user: UserHandle, val locked: Boolean)

class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    /** Every app outside private space. */
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _privateApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val privateApps: StateFlow<List<AppEntry>> = _privateApps

    private val _privateSpace = MutableStateFlow<PrivateSpace?>(null)
    val privateSpace: StateFlow<PrivateSpace?> = _privateSpace

    private val _pinned = MutableStateFlow<List<ShortcutInfo>>(emptyList())
    /** Shortcuts pinned to ISAC, like web apps added from the browser. */
    val pinned: StateFlow<List<ShortcutInfo>> = _pinned

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
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) {
            scope.launch { refreshPinned() }
        }
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        // Private space and work profile locking and unlocking.
        val filter = IntentFilter().apply {
            listOf(
                "android.intent.action.PROFILE_AVAILABLE", "android.intent.action.PROFILE_UNAVAILABLE",
                "android.intent.action.MANAGED_PROFILE_AVAILABLE", "android.intent.action.MANAGED_PROFILE_UNAVAILABLE",
            ).forEach(::addAction)
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { scope.launch { refresh() } }
        }, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun isPrivate(user: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT < 35) return false
        return runCatching { launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE }
            .getOrDefault(false)
    }

    suspend fun refresh() {
        val (list, privateUser) = withContext(Dispatchers.IO) {
            val me = Process.myUserHandle()
            var priv: UserHandle? = null
            val all = launcherApps.profiles.flatMap { user ->
                val private = isPrivate(user)
                if (private) priv = user
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
                            isWork = user != me && !private,
                            isPrivate = private,
                            category = categoryOf(info.applicationInfo),
                            firstInstall = info.firstInstallTime,
                            info = info,
                        )
                    }
            }.sortedBy { it.label.lowercase() }
            all to priv
        }
        _apps.value = list.filter { !it.isPrivate }
        _privateApps.value = list.filter { it.isPrivate }
        _privateSpace.value = privateUser?.let { PrivateSpace(it, runCatching { userManager.isQuietModeEnabled(it) }.getOrDefault(true)) }
        refreshPinned()
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

    // ---- private space ----

    /**
     * Locks or unlocks private space. Android only allows this for the default home app.
     * Returns false if it wasn't allowed.
     */
    fun setPrivateLocked(locked: Boolean): Boolean {
        val ps = _privateSpace.value ?: return false
        return runCatching { userManager.requestQuietModeEnabled(locked, ps.user); true }.getOrDefault(false)
    }

    // ---- shortcuts ----

    /** Shortcuts are only shared with the default home app. */
    val canUseShortcuts: Boolean get() = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    /** An app's long-press shortcuts (like "New chat" or "Navigate home"). */
    fun shortcuts(entry: AppEntry): List<ShortcutInfo> {
        if (!canUseShortcuts) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(entry.pkg)
            .setActivity(entry.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        return runCatching { launcherApps.getShortcuts(q, entry.user) }.getOrNull().orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(4)
    }

    suspend fun refreshPinned() {
        if (!canUseShortcuts) { _pinned.value = emptyList(); return }
        _pinned.value = withContext(Dispatchers.IO) {
            launcherApps.profiles.filter { !isPrivate(it) }.flatMap { user ->
                val q = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                runCatching { launcherApps.getShortcuts(q, user) }.getOrNull().orEmpty()
            }.filter { it.isEnabled }.sortedBy { it.shortLabel?.toString()?.lowercase() }
        }
    }

    fun startShortcut(info: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(info, null, null) }
    }

    fun shortcutIcon(info: ShortcutInfo, dpi: Int): Drawable? = runCatching { launcherApps.getShortcutIconDrawable(info, dpi) }.getOrNull()

    /** Removes a shortcut that was pinned to ISAC. */
    fun unpin(info: ShortcutInfo) {
        val keep = _pinned.value.filter { it.`package` == info.`package` && it.userHandle == info.userHandle && it.id != info.id }.map { it.id }
        runCatching { launcherApps.pinShortcuts(info.`package`, keep, info.userHandle) }
        scope.launch { refreshPinned() }
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
