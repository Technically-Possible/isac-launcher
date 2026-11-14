package app.isac.launcher.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [mono] is true when the bitmap is a single-color glyph (Android 13 themed icon layer). */
class AppIcon(val bitmap: ImageBitmap, val mono: Boolean)

class IconCache(private val context: Context) {
    private val cache = LruCache<String, AppIcon>(500)
    private val sizePx = (context.resources.displayMetrics.density * 56).toInt()

    fun peek(entry: AppEntry, full: Boolean): AppIcon? = cache.get(cacheKey(entry, full))

    suspend fun load(entry: AppEntry, full: Boolean): AppIcon = withContext(Dispatchers.IO) {
        val k = cacheKey(entry, full)
        cache.get(k) ?: create(entry, full).also { cache.put(k, it) }
    }

    fun clear() = cache.evictAll()

    private fun cacheKey(entry: AppEntry, full: Boolean) = entry.key + if (full) "|full" else "|mono"

    private fun create(entry: AppEntry, full: Boolean): AppIcon {
        val dpi = context.resources.displayMetrics.densityDpi
        val drawable = entry.info.getIcon(dpi)
        val s = sizePx
        if (!full && Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable) {
            val mono = drawable.monochrome
            if (mono != null) {
                // Adaptive layers are 108dp with the visible 72dp in the middle, so the layer is drawn at 1.5x.
                val bmp = createBitmap(s, s)
                mono.setBounds(-s / 4, -s / 4, s * 5 / 4, s * 5 / 4)
                mono.draw(Canvas(bmp))
                return AppIcon(bmp.asImageBitmap(), mono = true)
            }
        }
        return AppIcon(drawable.toBitmap(s, s).asImageBitmap(), mono = false)
    }
}
