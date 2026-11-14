package app.isac.launcher.isac

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ISAC's voice: Android's built-in text-to-speech, started only when first needed. */
class Isac(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun speak(text: String) {
        val engine = tts ?: TextToSpeech(context.applicationContext, this).also { tts = it }
        if (ready) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "isac") else pending = text
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(1.0f)
            pending?.let { speak(it) }
        }
        pending = null
    }
}

/** Lines written for this app. They fill in real numbers; no game dialogue is used. */
object IsacLines {
    fun greeting(now: Long): String {
        val h = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        return when (h) {
            in 5..11 -> "Good morning, Agent."
            in 12..17 -> "Good afternoon, Agent."
            in 18..23 -> "Good evening, Agent."
            else -> "It's late, Agent. Rest is part of the mission."
        }
    }

    fun status(now: Long, battery: Int, charging: Boolean, nextAlarm: Long?, use24h: Boolean): String {
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.UK)
        val time = Instant.ofEpochMilli(now).atZone(zone).format(fmt)
        val power = when {
            charging -> "Power cell at $battery percent and recharging."
            battery < 15 -> "Warning. Power cell critical at $battery percent. Find a charger."
            else -> "Power cell at $battery percent."
        }
        val alarm = nextAlarm?.let {
            val t = Instant.ofEpochMilli(it).atZone(zone)
            val day = if (t.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) "today" else
                t.format(DateTimeFormatter.ofPattern("EEEE", Locale.UK))
            " Next alarm ${t.format(fmt)} $day."
        } ?: ""
        return "${greeting(now)} The time is $time. $power$alarm"
    }

    fun newItem(label: String) = "New item added to inventory: $label."
    fun charger(on: Boolean) = if (on) "External power detected. Recharging." else "External power disconnected."
}

/** The Flashlight skill. */
class Torch(context: Context) {
    private val cm = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = runCatching {
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    val available: Boolean get() = cameraId != null

    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on

    init {
        if (cameraId != null) {
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) _on.value = enabled
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    fun toggle() {
        val id = cameraId ?: return
        runCatching { cm.setTorchMode(id, !_on.value) }
    }
}
