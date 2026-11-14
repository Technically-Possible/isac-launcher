package app.isac.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.isac.launcher.data.Backdrop
import app.isac.launcher.data.ExplorationTracker
import app.isac.launcher.data.Graphless
import app.isac.launcher.data.Tracking
import app.isac.launcher.data.WidgetSlot
import app.isac.launcher.ui.LauncherRoot
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    /** A widget waiting for bind permission or its configure screen. */
    private data class PendingWidget(val slot: WidgetSlot, val id: Int, val info: AppWidgetProviderInfo)
    private var pendingWidget: PendingWidget? = null

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrCommit(p) else cancelWidget(p.id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            LauncherRoot(vm, onPickWidget = ::pickWidget)
        }
        acceptPinRequest(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settings.collect {
                    applyBackdrop(it.backdrop)
                    applyStatusBar(it.hideStatusBar)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Coming back from another app can bring the status bar back; hide it again.
        if (hasFocus) applyStatusBar(vm.settings.value.hideStatusBar)
    }

    private fun applyStatusBar(hide: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    override fun onStart() {
        super.onStart()
        Graphless.isUiVisible = true
        runCatching { graph.widgets.host.startListening() }
        ExplorationTracker.start(this)
    }

    override fun onStop() {
        super.onStop()
        Graphless.isUiVisible = false
        runCatching { graph.widgets.host.stopListening() }
        // Without "always", tracking only runs while ISAC is on screen.
        if (graph.prefs.tracking != Tracking.ALWAYS) ExplorationTracker.stop(this)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /** "Add to home screen" from a browser or app: keep the shortcut and show it in Inventory. */
    private fun acceptPinRequest(intent: Intent?) {
        if (intent?.action != LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT) return
        val request = runCatching { getSystemService(LauncherApps::class.java).getPinItemRequest(intent) }.getOrNull() ?: return
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return
        val label = request.shortcutInfo?.shortLabel
        if (runCatching { request.accept() }.getOrDefault(false)) {
            lifecycleScope.launch { graph.apps.refreshPinned() }
            vm.showToast("New item added to inventory: ${label ?: "shortcut"}.")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptPinRequest(intent)
        // Pressing Home while the launcher is showing.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.homeEvents.tryEmit(Unit)
        }
    }

    private fun applyBackdrop(mode: Backdrop) {
        if (mode == Backdrop.WALLPAPER) {
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(0xFF0B0C0D.toInt()))
        }
    }

    // ---------------------------------------------------------------- widget slot

    private fun pickWidget(slot: WidgetSlot, info: AppWidgetProviderInfo) {
        val widgets = graph.widgets
        val id = widgets.allocate()
        val p = PendingWidget(slot, id, info)
        pendingWidget = p
        if (widgets.bindIfAllowed(id, info)) {
            configureOrCommit(p)
        } else {
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
            )
        }
    }

    private fun configureOrCommit(p: PendingWidget) {
        if (p.info.configure != null) {
            try {
                graph.widgets.host.startAppWidgetConfigureActivityForResult(this, p.id, 0, REQ_CONFIGURE, null)
                return
            } catch (_: Exception) {
                // Some widgets can't be configured from a third-party launcher; add it anyway.
            }
        }
        commitWidget(p)
    }

    private fun commitWidget(p: PendingWidget) {
        graph.widgets.commit(p.slot, p.id)
        pendingWidget = null
        vm.overlay = Overlay.None
    }

    private fun cancelWidget(id: Int) {
        graph.widgets.cancel(id)
        pendingWidget = null
    }

    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            val p = pendingWidget ?: return
            if (resultCode == RESULT_OK) commitWidget(p) else cancelWidget(p.id)
        }
    }

    private companion object {
        const val REQ_CONFIGURE = 0x15AC
    }
}
