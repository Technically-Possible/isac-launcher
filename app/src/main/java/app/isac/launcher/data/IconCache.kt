package app.isac.launcher.data

import android.content.Context
import android.content.pm.ShortcutInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
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

/** How an icon is drawn: the themed one-colour glyph, the app's own icon, or an icon pack's version. */
sealed interface IconSource {
    data object Mono : IconSource
    data object Full : IconSource
    data class Pack(val pkg: String) : IconSource

    companion object {
        fun of(style: IconStyle, pack: String): IconSource = when (style) {
            IconStyle.ORIGINAL -> Full
            IconStyle.PACK -> if (pack.isBlank()) Full else Pack(pack)
            else -> Mono
        }
    }
}

class IconCache(private val context: Context) {
    private val cache = LruCache<String, AppIcon>(600)
    private val sizePx = (context.resources.displayMetrics.density * 56).toInt()
    private val dpi = context.resources.displayMetrics.densityDpi
    private val packs = HashMap<String, IconPack>()

    fun peek(entry: AppEntry, source: IconSource): AppIcon? = cache.get(cacheKey(entry.key, source))

    suspend fun load(entry: AppEntry, source: IconSource): AppIcon = withContext(Dispatchers.IO) {
        val k = cacheKey(entry.key, source)
        cache.get(k) ?: create(entry, source).also { cache.put(k, it) }
    }

    suspend fun loadShortcut(info: ShortcutInfo, launcher: AppRepository): AppIcon? = withContext(Dispatchers.IO) {
        val k = "sc:${info.`package`}/${info.id}"
        cache.get(k) ?: launcher.shortcutIcon(info, dpi)?.let { AppIcon(it.toBitmap(sizePx, sizePx).asImageBitmap(), false) }?.also { cache.put(k, it) }
    }

    /** Loads every app's icon in the background, so lists don't fill in while scrolling. */
    suspend fun prewarm(entries: List<AppEntry>, source: IconSource) = withContext(Dispatchers.IO) {
        entries.forEach { e -> if (peek(e, source) == null) runCatching { load(e, source) } }
    }

    fun clear() {
        cache.evictAll()
        packs.clear()
    }

    private fun cacheKey(key: String, source: IconSource) = key + when (source) {
        IconSource.Mono -> "|mono"
        IconSource.Full -> "|full"
        is IconSource.Pack -> "|pack:${source.pkg}"
    }

    private fun create(entry: AppEntry, source: IconSource): AppIcon {
        val drawable = entry.info.getIcon(dpi)
        val s = sizePx
        when (source) {
            IconSource.Mono -> if (Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable) {
                val mono = drawable.monochrome
                if (mono != null) {
                    // Adaptive layers are 108dp with the visible 72dp in the middle, so the layer is drawn at 1.5x.
                    val bmp = createBitmap(s, s)
                    mono.setBounds(-s / 4, -s / 4, s * 5 / 4, s * 5 / 4)
                    mono.draw(Canvas(bmp))
                    return AppIcon(bmp.asImageBitmap(), mono = true)
                }
            }
            is IconSource.Pack -> {
                val pack = synchronized(packs) { packs.getOrPut(source.pkg) { IconPack(context, source.pkg) } }
                if (pack.loaded) {
                    pack.iconFor(entry.component, dpi)?.let { return AppIcon(it.toBitmap(s, s).asImageBitmap(), false) }
                    pack.back(entry.component, dpi)?.let { back -> return AppIcon(composite(back, drawable, pack).asImageBitmap(), false) }
                }
            }
            IconSource.Full -> Unit
        }
        return AppIcon(drawable.toBitmap(s, s).asImageBitmap(), mono = false)
    }

    /** An app the pack doesn't cover: the original icon, scaled onto the pack's background and shaped by its mask. */
    private fun composite(back: Drawable, original: Drawable, pack: IconPack): android.graphics.Bitmap {
        val s = sizePx
        val out = createBitmap(s, s)
        val c = Canvas(out)
        back.setBounds(0, 0, s, s); back.draw(c)
        val inner = (s * pack.scale).toInt().coerceIn(s / 4, s)
        val off = (s - inner) / 2
        val layer = createBitmap(s, s)
        Canvas(layer).also { lc -> original.setBounds(off, off, off + inner, off + inner); original.draw(lc) }
        pack.mask(dpi)?.let { m ->
            val maskBmp = m.toBitmap(s, s)
            Canvas(layer).drawBitmap(maskBmp, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        c.drawBitmap(layer, 0f, 0f, null)
        pack.upon(dpi)?.let { u -> u.setBounds(0, 0, s, s); u.draw(c) }
        return out
    }
}
