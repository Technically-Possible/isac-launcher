package app.isac.launcher.data

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.pow

/** One non-app search result. */
data class SearchHit(val kind: String, val title: String, val detail: String, val intent: Intent?)

object Search {
    // ---------------------------------------------------------------- calculator

    /** Works out sums like "12.5 * (3 + 4)" or "2^10". Returns null if the text isn't a sum. */
    fun calculate(text: String): String? {
        val src = text.replace("×", "*").replace("÷", "/").replace(",", "").replace(" ", "")
        if (src.none { it in "+-*/^%" } || src.none(Char::isDigit) || !src.all { it.isDigit() || it in "+-*/^%.()" }) return null
        var i = 0
        fun expr(): Double {
            fun atom(): Double {
                if (i < src.length && src[i] == '-') { i++; return -atom() }
                if (i < src.length && src[i] == '(') { i++; val v = expr(); if (i < src.length && src[i] == ')') i++; return v }
                val start = i
                while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
                if (start == i) throw IllegalArgumentException()
                var v = src.substring(start, i).toDouble()
                if (i < src.length && src[i] == '%') { i++; v /= 100 }
                return v
            }
            fun power(): Double { val b = atom(); return if (i < src.length && src[i] == '^') { i++; b.pow(power()) } else b }
            fun term(): Double {
                var v = power()
                while (i < src.length && src[i] in "*/") { val op = src[i++]; val r = power(); v = if (op == '*') v * r else v / r }
                return v
            }
            var v = term()
            while (i < src.length && src[i] in "+-") { val op = src[i++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        val v = runCatching { expr() }.getOrNull() ?: return null
        if (i != src.length || v.isNaN() || v.isInfinite()) return null
        return number(v)
    }

    private fun number(v: Double): String =
        if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) "%,d".format(Locale.UK, v.toLong())
        else "%,.4f".format(Locale.UK, v).trimEnd('0').trimEnd('.')

    // ---------------------------------------------------------------- units

    private val LENGTH = mapOf("mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0, "in" to 0.0254, "inch" to 0.0254,
        "inches" to 0.0254, "ft" to 0.3048, "foot" to 0.3048, "feet" to 0.3048, "yd" to 0.9144, "yard" to 0.9144, "yards" to 0.9144,
        "mi" to 1609.344, "mile" to 1609.344, "miles" to 1609.344, "metre" to 1.0, "metres" to 1.0, "meter" to 1.0, "meters" to 1.0)
    private val MASS = mapOf("g" to 1.0, "kg" to 1000.0, "oz" to 28.349523, "lb" to 453.59237, "lbs" to 453.59237,
        "st" to 6350.29318, "stone" to 6350.29318, "gram" to 1.0, "grams" to 1.0, "pound" to 453.59237, "pounds" to 453.59237)
    private val VOLUME = mapOf("ml" to 1.0, "l" to 1000.0, "litre" to 1000.0, "litres" to 1000.0, "pint" to 568.26125,
        "pints" to 568.26125, "gal" to 4546.09, "gallon" to 4546.09, "gallons" to 4546.09, "cup" to 284.131, "cups" to 284.131)
    private val SPEED = mapOf("mph" to 0.44704, "kph" to 0.2777778, "kmh" to 0.2777778, "km/h" to 0.2777778, "m/s" to 1.0, "knots" to 0.514444)

    /** Converts things like "10 miles in km", "72 f to c" or "5 ft in m". Returns null if it isn't a conversion. */
    fun convert(text: String): String? {
        val m = Regex("^\\s*(-?[\\d.,]+)\\s*°?\\s*([a-z/]+)\\s+(?:in|to|as)\\s+°?\\s*([a-z/]+)\\s*$").find(text.lowercase()) ?: return null
        val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val from = m.groupValues[2]
        val to = m.groupValues[3]
        val temps = setOf("c", "f", "k", "celsius", "fahrenheit", "kelvin")
        if (from in temps && to in temps) {
            val c = when (from[0]) { 'f' -> (v - 32) * 5 / 9; 'k' -> v - 273.15; else -> v }
            val out = when (to[0]) { 'f' -> c * 9 / 5 + 32; 'k' -> c + 273.15; else -> c }
            return "${number(Math.round(out * 10) / 10.0)}°${to[0].uppercaseChar()}"
        }
        for (table in listOf(LENGTH, MASS, VOLUME, SPEED)) {
            val a = table[from]
            val b = table[to]
            if (a != null && b != null) return "${number(Math.round(v * a / b * 1000) / 1000.0)} $to"
        }
        return null
    }

    // ---------------------------------------------------------------- settings

    private val SETTINGS = listOf(
        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS, "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "Display" to Settings.ACTION_DISPLAY_SETTINGS, "Sound and vibration" to Settings.ACTION_SOUND_SETTINGS,
        "Battery" to Intent.ACTION_POWER_USAGE_SUMMARY, "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "Apps" to Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, "Notifications" to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Security" to Settings.ACTION_SECURITY_SETTINGS,
        "Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS, "Date and time" to Settings.ACTION_DATE_SETTINGS,
        "Language" to Settings.ACTION_LOCALE_SETTINGS, "Mobile network" to Settings.ACTION_DATA_ROAMING_SETTINGS,
        "Aeroplane mode" to Settings.ACTION_AIRPLANE_MODE_SETTINGS, "NFC" to Settings.ACTION_NFC_SETTINGS,
        "Do Not Disturb" to "android.settings.ZEN_MODE_SETTINGS", "Default apps" to Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        "Developer options" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "About phone" to Settings.ACTION_DEVICE_INFO_SETTINGS,
        "Home app" to Settings.ACTION_HOME_SETTINGS, "Privacy" to Settings.ACTION_PRIVACY_SETTINGS,
    )

    fun settings(query: String): List<SearchHit> = SETTINGS.filter { it.first.contains(query, true) }.take(3).map { (name, action) ->
        SearchHit("SETTING", name, "Android settings", Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------------------------------------------------------------- contacts

    fun hasContacts(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun contacts(context: Context, query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        if (!hasContacts(context) || query.length < 2) return@withContext emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val cols = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val out = ArrayList<SearchHit>()
        runCatching {
            context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < 4) {
                    val look = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                    out += SearchHit("CONTACT", c.getString(2) ?: continue, "Contact",
                        Intent(Intent.ACTION_VIEW, look).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        out
    }

    fun web(query: String) = SearchHit("WEB", "Search the web for “$query”", "Opens your browser",
        Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Lets ISAC lock the screen and open the notification shade, the same way other launchers do it:
 * a small accessibility service the user switches on. It doesn't watch or read anything on screen.
 */
class GestureService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile private var instance: GestureService? = null
        val enabled: Boolean get() = instance != null

        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
        fun notifications(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        fun component(context: Context) = ComponentName(context, GestureService::class.java)
    }
}
