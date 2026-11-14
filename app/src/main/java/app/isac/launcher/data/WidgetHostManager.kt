package app.isac.launcher.data

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/** Places on the Intel page that can hold a regular Android widget. */
enum class WidgetSlot(val key: String, val title: String, val hints: List<String>) {
    WORK("work", "Work status", listOf("slack", "status", "teams")),
    PHOTO1("photo1", "Photo frame 1", listOf("photo", "gallery", "picture", "album")),
    PHOTO2("photo2", "Photo frame 2", listOf("photo", "gallery", "picture", "album")),
    ;

    val isPhoto get() = this != WORK
}

/**
 * Hosts widgets in the Intel page's slots: the two photo frames and work status.
 * Android doesn't let one launcher reuse another launcher's widgets, so each slot holds a new copy.
 */
class WidgetHostManager(private val context: Context, private val prefs: Prefs) {
    val host: AppWidgetHost = IsacWidgetHost(context, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private val _ids = MutableStateFlow(
        WidgetSlot.entries.associateWith { slot ->
            prefs.widgetId(slot).takeIf { it != -1 && manager.getAppWidgetInfo(it) != null } ?: -1
        },
    )
    val ids: StateFlow<Map<WidgetSlot, Int>> = _ids

    fun isSuggested(slot: WidgetSlot, info: AppWidgetProviderInfo): Boolean {
        val text = (info.loadLabel(context.packageManager) + " " + info.provider.packageName).lowercase()
        return slot.hints.any { it in text }
    }

    /** Every installed widget, with the likely matches for [slot] first. */
    fun providers(slot: WidgetSlot): List<AppWidgetProviderInfo> {
        val pm = context.packageManager
        return manager.installedProviders.sortedWith(
            compareBy<AppWidgetProviderInfo>({ !isSuggested(slot, it) }, { appLabel(it).lowercase() }, { it.loadLabel(pm).lowercase() }),
        )
    }

    fun appLabel(info: AppWidgetProviderInfo): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
    }.getOrDefault(info.provider.packageName)

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun commit(slot: WidgetSlot, id: Int) {
        val old = _ids.value[slot] ?: -1
        if (old != -1 && old != id) runCatching { host.deleteAppWidgetId(old) }
        prefs.setWidgetId(slot, id)
        _ids.value = _ids.value + (slot to id)
    }

    fun cancel(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    fun remove(slot: WidgetSlot) {
        val old = _ids.value[slot] ?: -1
        if (old != -1) runCatching { host.deleteAppWidgetId(old) }
        prefs.setWidgetId(slot, -1)
        _ids.value = _ids.value + (slot to -1)
    }

    /** Tells the widget how big it is on screen, so photo widgets crop to the frame. */
    fun updateSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        val key = "${widthDp.toInt()}x${heightDp.toInt()}"
        if (view.tag == key || widthDp < 1 || heightDp < 1) return
        view.tag = key
        if (Build.VERSION.SDK_INT >= 31) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
        }
    }

    companion object {
        const val HOST_ID = 0x15AC
    }
}

private class IsacWidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        IsacWidgetHostView(context)
}

/** A widget view that also notices a long press, so a hosted widget can be changed or removed. */
class IsacWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null

    private val timeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val longPress = Runnable {
        fired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.x; downY = ev.y
                postDelayed(longPress, timeout)
            }
            MotionEvent.ACTION_MOVE ->
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
    }

    // Once the long press fires, take the rest of the gesture so the widget doesn't also see a tap.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return fired
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return true
    }
}
