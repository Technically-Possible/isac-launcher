package app.isac.launcher.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val pkg: String, val label: String)

/**
 * Icon packs made for Nova, ADW and similar launchers. Each pack maps app components to its own
 * drawables in an appfilter.xml; apps it doesn't cover get the pack's background, mask and overlay.
 */
class IconPack(private val context: Context, val pkg: String) {
    private val map = HashMap<String, String>()
    private val backs = ArrayList<String>()
    private var mask: String? = null
    private var upon: String? = null
    var scale = 1f
        private set
    private var res: Resources? = null
    val loaded: Boolean get() = res != null

    init { load() }

    @SuppressLint("DiscouragedApi")
    private fun load() {
        val r = runCatching { context.packageManager.getResourcesForApplication(pkg) }.getOrNull() ?: return
        res = r
        val parser: XmlPullParser? = runCatching {
            val id = r.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) r.getXml(id) else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(r.assets.open("appfilter.xml"), "UTF-8")
                }
            }
        }.getOrNull()
        parser ?: return
        runCatching {
            var e = parser.eventType
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val comp = parser.getAttributeValue(null, "component")
                            val draw = parser.getAttributeValue(null, "drawable")
                            if (comp != null && draw != null) {
                                comp.removePrefix("ComponentInfo{").removeSuffix("}").takeIf { '/' in it }?.let { map[normalise(it)] = draw }
                            }
                        }
                        "iconback" -> for (i in 0 until parser.attributeCount) backs += parser.getAttributeValue(i)
                        "iconmask" -> mask = parser.getAttributeValue(null, "img1")
                        "iconupon" -> upon = parser.getAttributeValue(null, "img1")
                        "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                    }
                }
                e = parser.next()
            }
        }
    }

    /** "pkg/.Cls" and "pkg/pkg.Cls" both become "pkg/pkg.Cls". */
    private fun normalise(flat: String): String {
        val (p, c) = flat.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return "$p/${if (c.startsWith(".")) p + c else c}"
    }

    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables")
    private fun drawable(name: String?, density: Int): Drawable? {
        val r = res ?: return null
        if (name.isNullOrBlank()) return null
        val id = r.getIdentifier(name, "drawable", pkg).takeIf { it != 0 } ?: r.getIdentifier(name, "mipmap", pkg)
        if (id == 0) return null
        return runCatching { r.getDrawableForDensity(id, density, null) }.getOrNull()
    }

    fun iconFor(component: ComponentName, density: Int): Drawable? =
        drawable(map[normalise(component.flattenToString())], density)

    fun back(component: ComponentName, density: Int): Drawable? =
        if (backs.isEmpty()) null else drawable(backs[Math.floorMod(component.hashCode(), backs.size)], density)

    fun mask(density: Int) = drawable(mask, density)
    fun upon(density: Int) = drawable(upon, density)

    companion object {
        private val ACTIONS = listOf(
            "com.novalauncher.THEME", "org.adw.launcher.THEMES", "com.gau.go.launcherex.theme",
            "com.teslacoilsw.launcher.THEME", "com.anddoes.launcher.THEME", "ch.deletescape.lawnchair.ICONPACK",
        )

        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            return ACTIONS.flatMap { a ->
                runCatching { pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
            }.map { it.activityInfo.packageName }.distinct().map { p ->
                IconPackInfo(p, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p))
            }.sortedBy { it.label.lowercase() }
        }
    }
}
