package app.isac.launcher.data

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow

data class Battery(val percent: Int = 0, val charging: Boolean = false, val tempC: Float? = null) {
    val critical get() = percent < 15 && !charging
    /** Phones start to throttle around here. */
    val hot get() = (tempC ?: 0f) >= 43f
}

fun batteryFlow(context: Context): Flow<Battery> = callbackFlow {
    fun read(i: Intent?): Battery {
        if (i == null) return Battery()
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        return Battery(level * 100 / scale, charging, temp)
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { trySend(read(i)) }
    }
    val sticky = ContextCompat.registerReceiver(
        context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    trySend(read(sticky))
    awaitClose { context.unregisterReceiver(receiver) }
}

/** True while the phone's battery saver is on. */
fun powerSaveFlow(context: Context): Flow<Boolean> = callbackFlow {
    val pm = context.getSystemService(android.os.PowerManager::class.java)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { trySend(pm.isPowerSaveMode) }
    }
    ContextCompat.registerReceiver(
        context, receiver, IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_EXPORTED,
    )
    trySend(pm.isPowerSaveMode)
    awaitClose { context.unregisterReceiver(receiver) }
}

/** Emits the current time now and then at the start of every minute. */
fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay(60_000 - now % 60_000 + 50)
    }
}

/** Next alarm trigger time, updated whenever the user changes alarms. */
fun nextAlarmFlow(context: Context): Flow<Long?> = callbackFlow {
    val am = context.getSystemService(AlarmManager::class.java)
    fun read() = am.nextAlarmClock?.triggerTime
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { trySend(read()) }
    }
    ContextCompat.registerReceiver(
        context, receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_EXPORTED,
    )
    trySend(read())
    awaitClose { context.unregisterReceiver(receiver) }
}
