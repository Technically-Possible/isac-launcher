package app.isac.launcher.isac

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.isac.launcher.data.AppEntry
import java.util.Locale

/** Listens for one spoken command using Android's speech recognition, on the phone where available. */
class VoiceInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    private val onDevice: Boolean get() =
        android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /**
     * True only when the phone has a real speech service set up. Some assistant apps also register
     * recognisers, but they aren't for general use, so they don't count.
     */
    val usable: Boolean get() {
        if (onDevice) return true
        val default = android.provider.Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        return !default.isNullOrBlank() && SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun start(
        onPartial: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onResult: (String?) -> Unit,
    ) {
        stop()
        val r = if (onDevice && android.os.Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { onResult(null) }
            override fun onResults(results: Bundle?) {
                onResult(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()),
        )
    }

    fun stop() {
        recognizer?.runCatching { cancel(); destroy() }
        recognizer = null
    }
}

/** What a spoken command asks for. The view model carries it out. */
sealed interface Command {
    data class Open(val app: AppEntry) : Command
    data class Timer(val seconds: Int, val spoken: String) : Command
    data class Alarm(val hour: Int, val minute: Int) : Command
    data class Torch(val on: Boolean?) : Command
    data object Status : Command
    data object Weather : Command
    data object Pulse : Command
    data object Home : Command
    data class Tab(val index: Int) : Command
    data class Unknown(val heard: String) : Command
}

object CommandParser {
    private val NUMBERS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "forty five" to 45, "fifty" to 50, "sixty" to 60, "ninety" to 90, "half an" to 30,
    )

    private val ONES = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val TENS = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50)

    /** The offline engine writes numbers as words: "seven thirty p m" becomes "7 30 pm". */
    fun normalizeNumbers(s: String): String {
        val words = s.split(' ').filter { it.isNotBlank() }
        val out = ArrayList<String>()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val tens = TENS[w]
            if (tens != null) {
                val next = words.getOrNull(i + 1)?.let { ONES[it] }
                if (next != null && next in 1..9) { out += (tens + next).toString(); i += 2 } else { out += tens.toString(); i++ }
                continue
            }
            val one = ONES[w]
            if (one != null) { out += one.toString(); i++; continue }
            // "seven oh five" -> "7 05"
            if (w == "oh" && out.lastOrNull()?.toIntOrNull() != null) {
                val next = words.getOrNull(i + 1)?.let { ONES[it] }
                if (next != null && next < 10) { out += "0$next"; i += 2; continue }
            }
            out += w
            i++
        }
        return out.joinToString(" ").replace(" a m", " am").replace(" p m", " pm")
    }

    fun parse(raw: String, apps: List<AppEntry>, tabs: List<String>): Command {
        var t = raw.lowercase(Locale.UK).trim().replace(Regex("[,.!?]"), "")
        t = t.removePrefix("hey ").removePrefix("isaac ").removePrefix("isac ").removePrefix("ice ac ").trim()
        t = normalizeNumbers(t)

        Regex("^(open|launch|start|run) (.+)$").find(t)?.let { m ->
            val name = m.groupValues[2].removePrefix("the ").removeSuffix(" app").trim()
            return bestApp(name, apps)?.let { Command.Open(it) } ?: Command.Unknown(raw)
        }
        if ("timer" in t) {
            val m = Regex("(\\d+|[a-z]+(?: [a-z]+)?) (second|minute|hour)s?").find(t)
            if (m != null) {
                val n = m.groupValues[1].toIntOrNull() ?: NUMBERS[m.groupValues[1]] ?: NUMBERS[m.groupValues[1].substringAfterLast(' ')]
                if (n != null) {
                    val unit = m.groupValues[2]
                    val secs = n * when (unit) { "second" -> 1; "minute" -> 60; else -> 3600 }
                    return Command.Timer(secs, "$n $unit${if (n == 1) "" else "s"}")
                }
            }
        }
        if ("alarm" in t) {
            val m = Regex("(\\d{1,2})(?:[: ](\\d{2}))? ?(am|pm|a\\.m\\.|p\\.m\\.)?").find(t.substringAfter("alarm"))
            if (m != null) {
                var h = m.groupValues[1].toInt()
                val min = m.groupValues[2].toIntOrNull() ?: 0
                val ampm = m.groupValues[3]
                if (ampm.startsWith("p") && h < 12) h += 12
                if (ampm.startsWith("a") && h == 12) h = 0
                if (h in 0..23 && min in 0..59) return Command.Alarm(h, min)
            }
        }
        if ("torch" in t || "flashlight" in t || "light" in t) {
            return Command.Torch(if (" on" in " $t") true else if ("off" in t) false else null)
        }
        if (listOf("status", "report", "sitrep", "how am i").any { it in t }) return Command.Status
        if ("weather" in t || "temperature" in t) return Command.Weather
        if ("pulse" in t || "scan" in t) return Command.Pulse
        if (listOf("take me home", "navigate home", "go home", "directions home", "base of operations").any { it in t }) return Command.Home
        tabs.forEachIndexed { i, name -> if (t == name.lowercase() || t == "show ${name.lowercase()}") return Command.Tab(i) }
        // "WhatsApp" on its own also opens it.
        bestApp(t, apps)?.let { if (it.label.lowercase() == t) return Command.Open(it) }
        return Command.Unknown(raw)
    }

    /** Closest app name, allowing for small recognition mistakes. */
    fun bestApp(name: String, apps: List<AppEntry>): AppEntry? {
        if (name.isBlank()) return null
        val n = name.lowercase()
        apps.firstOrNull { it.label.lowercase() == n }?.let { return it }
        apps.filter { it.label.lowercase().startsWith(n) || n.startsWith(it.label.lowercase()) }.minByOrNull { it.label.length }?.let { return it }
        apps.filter { n in it.label.lowercase() }.minByOrNull { it.label.length }?.let { return it }
        val scored = apps.map { it to similarity(n, it.label.lowercase()) }.maxByOrNull { it.second }
        return scored?.takeIf { it.second >= 0.72 }?.first
    }

    private fun similarity(a: String, b: String): Double {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return 1.0 - d[a.length][b.length].toDouble() / maxOf(a.length, b.length, 1)
    }

    fun timerIntent(seconds: Int) = Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        .putExtra(AlarmClock.EXTRA_MESSAGE, "ISAC")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun alarmIntent(hour: Int, minute: Int) = Intent(AlarmClock.ACTION_SET_ALARM)
        .putExtra(AlarmClock.EXTRA_HOUR, hour)
        .putExtra(AlarmClock.EXTRA_MINUTES, minute)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        .putExtra(AlarmClock.EXTRA_MESSAGE, "ISAC")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun navigateIntent(lat: Double, lon: Double) =
        Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun mapIntent(lat: Double, lon: Double) =
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(Base of Operations)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
