package app.isac.launcher.isac

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short vibration patterns, so the menus feel like a controller. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    var enabled = true

    private fun play(effect: () -> VibrationEffect) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(effect()) }
    }

    private fun predefined(id: Int, fallbackMs: Long) = play {
        if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(id)
        else VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    fun tick() = predefined(VibrationEffect.EFFECT_TICK, 10)
    fun confirm() = predefined(VibrationEffect.EFFECT_CLICK, 20)
    fun heavy() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK, 40)

    /** A rising double beat, for the Pulse scan going out. */
    fun pulse() = play { VibrationEffect.createWaveform(longArrayOf(0, 25, 70, 45), intArrayOf(0, 120, 0, 255), -1) }

    /** Three strong beats for a level-up or a new area. */
    fun reward() = play { VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 40, 60, 90), intArrayOf(0, 200, 0, 200, 0, 255), -1) }

    /** One locator ping. [closeness] runs from 0 (far) to 1 (right here) and sets the strength. */
    fun ping(closeness: Float) = play {
        val amp = (60 + 195 * closeness.coerceIn(0f, 1f)).toInt()
        VibrationEffect.createOneShot(30, amp)
    }
}
