package app.isac.launcher.data

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.abs
import kotlin.math.sqrt

/** Phone sensors used for the HUD. Every flow stops listening as soon as nothing collects it. */
object Sensors {

    /**
     * How far the phone is tilted from the way it's usually held, from -1 to 1 on each axis.
     * The resting angle drifts slowly, so the backdrop eases back to centre when you hold still.
     */
    fun tilt(context: Context): Flow<Pair<Float, Float>> {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return emptyFlow()
        return callbackFlow {
            val r = FloatArray(9)
            val o = FloatArray(3)
            var basePitch = Float.NaN
            var baseRoll = Float.NaN
            var x = 0f
            var y = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(r, e.values)
                    SensorManager.getOrientation(r, o)
                    val pitch = Math.toDegrees(o[1].toDouble()).toFloat()
                    val roll = Math.toDegrees(o[2].toDouble()).toFloat()
                    if (basePitch.isNaN()) { basePitch = pitch; baseRoll = roll }
                    basePitch += (pitch - basePitch) * 0.01f
                    baseRoll += (roll - baseRoll) * 0.01f
                    val tx = ((roll - baseRoll) / 18f).coerceIn(-1f, 1f)
                    val ty = ((pitch - basePitch) / 18f).coerceIn(-1f, 1f)
                    x += (tx - x) * 0.2f
                    y += (ty - y) * 0.2f
                    trySend(x to y)
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { sm.unregisterListener(listener) }
        }
    }

    /** Compass heading in degrees from north (0-360). True north when [lat]/[lon] are known. */
    fun heading(context: Context, lat: Double?, lon: Double?): Flow<Float> {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return emptyFlow()
        val declination = if (lat != null && lon != null) {
            GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        } else 0f
        return callbackFlow {
            val r = FloatArray(9)
            val remapped = FloatArray(9)
            val o = FloatArray(3)
            var smooth = Float.NaN
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(r, e.values)
                    SensorManager.getOrientation(r, o)
                    // Held upright, the flat-phone azimuth is unstable, so measure along the camera axis instead.
                    if (abs(Math.toDegrees(o[1].toDouble())) > 45) {
                        SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                        SensorManager.getOrientation(remapped, o)
                    }
                    val az = ((Math.toDegrees(o[0].toDouble()).toFloat() + declination) + 360f) % 360f
                    smooth = if (smooth.isNaN()) az else {
                        var d = az - smooth
                        if (d > 180) d -= 360f
                        if (d < -180) d += 360f
                        (smooth + d * 0.15f + 360f) % 360f
                    }
                    trySend(smooth)
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            awaitClose { sm.unregisterListener(listener) }
        }
    }

    fun hasBarometer(context: Context) =
        context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    /** Air pressure in hPa. */
    fun pressure(context: Context): Flow<Float> {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return emptyFlow()
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) { trySend(e.values[0]) }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(listener, sensor, 2_000_000)
            awaitClose { sm.unregisterListener(listener) }
        }
    }

    fun altitudeMeters(hPa: Float): Float = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa)

    /** Emits once for each firm shake: two strong jolts within 600 ms. */
    fun shakes(context: Context): Flow<Unit> {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return emptyFlow()
        return callbackFlow {
            var lastJolt = 0L
            var lastShake = 0L
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    val (ax, ay, az) = Triple(e.values[0], e.values[1], e.values[2])
                    val g = sqrt(ax * ax + ay * ay + az * az) / SensorManager.GRAVITY_EARTH
                    if (g < 2.6f) return
                    val now = System.currentTimeMillis()
                    if (now - lastJolt in 120..600 && now - lastShake > 2_000) {
                        lastShake = now
                        trySend(Unit)
                    }
                    lastJolt = now
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            awaitClose { sm.unregisterListener(listener) }
        }
    }

    /** True while Do Not Disturb is on (for example after Pixel's "Flip to Shhh"). */
    fun stealth(context: Context): Flow<Boolean> = callbackFlow {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun read() = nm.currentInterruptionFilter.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { trySend(read()) }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        trySend(read())
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
