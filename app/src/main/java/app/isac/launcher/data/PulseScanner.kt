package app.isac.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow

enum class SignalKind(val label: String) {
    PHONE("PHONE"), WATCH("WEARABLE"), AUDIO("AUDIO"), COMPUTER("COMPUTER"), DEVICE("DEVICE")
}

data class Signal(
    val address: String,
    val name: String?,
    val kind: SignalKind,
    val rssi: Int,
    val txPower: Int?,
    val bonded: Boolean,
    val lastSeen: Long,
) {
    /** Rough distance from signal strength. Walls, bodies and pockets all change this a lot. */
    val meters: Float get() = estimateMeters(rssi, txPower)
}

fun estimateMeters(rssi: Int, txPower: Int?): Float {
    val tx = txPower?.takeIf { it in -100..20 }?.let { if (it > -20) it - 41 else it } ?: -59
    return 10.0.pow((tx - rssi) / (10 * 2.4)).toFloat().coerceIn(0.1f, 99f)
}

/**
 * The Pulse scan: nearby Bluetooth devices (with a rough distance each) and a count of Wi-Fi networks.
 * Also the locator, which follows one device's signal strength continuously.
 */
class PulseScanner(private val context: Context) {
    private val bt = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _signals = MutableStateFlow<List<Signal>>(emptyList())
    val signals: StateFlow<List<Signal>> = _signals

    private val _wifiCount = MutableStateFlow(0)
    val wifiCount: StateFlow<Int> = _wifiCount

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    /** Smoothed signal strength of the tracked device, and when it was last heard. */
    private val _tracked = MutableStateFlow<Pair<Float, Long>?>(null)
    val tracked: StateFlow<Pair<Float, Long>?> = _tracked

    private val found = LinkedHashMap<String, Signal>()

    val bluetoothOn: Boolean get() = bt?.isEnabled == true

    fun scanPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }.toTypedArray()

    fun hasPermission(): Boolean = scanPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun describe(r: ScanResult): Signal {
        val d: BluetoothDevice = r.device
        val name = runCatching { r.scanRecord?.deviceName ?: d.name }.getOrNull()?.takeIf { it.isNotBlank() }
        val major = runCatching { d.bluetoothClass?.majorDeviceClass }.getOrNull()
        val kind = when (major) {
            BluetoothClass.Device.Major.PHONE -> SignalKind.PHONE
            BluetoothClass.Device.Major.WEARABLE -> SignalKind.WATCH
            BluetoothClass.Device.Major.AUDIO_VIDEO -> SignalKind.AUDIO
            BluetoothClass.Device.Major.COMPUTER -> SignalKind.COMPUTER
            else -> guessKind(name)
        }
        val tx = r.scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }
        val bonded = runCatching { d.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
        return Signal(d.address, name, kind, r.rssi, tx, bonded, System.currentTimeMillis())
    }

    private fun guessKind(name: String?): SignalKind {
        val n = name?.lowercase() ?: return SignalKind.DEVICE
        return when {
            listOf("watch", "band", "fit", "galaxy watch", "amazfit", "mi band").any { it in n } -> SignalKind.WATCH
            listOf("buds", "airpods", "headphone", "earbud", "speaker", "soundcore", "jbl", "bose", "sony").any { it in n } -> SignalKind.AUDIO
            listOf("pixel", "iphone", "galaxy", "phone", "oneplus").any { it in n } -> SignalKind.PHONE
            listOf("macbook", "laptop", "desktop", "pc").any { it in n } -> SignalKind.COMPUTER
            else -> SignalKind.DEVICE
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val s = describe(result)
            found[s.address] = s
            _signals.value = found.values.sortedByDescending { it.rssi }
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
    }

    /** Runs a scan for [millis] and then calls [done] with the number of signals found. */
    @SuppressLint("MissingPermission")
    fun pulse(millis: Long = 6_000, done: (Int) -> Unit) {
        if (!hasPermission()) { done(0); return }
        stopScan()
        found.clear()
        _signals.value = emptyList()
        _scanning.value = true
        runCatching { @Suppress("DEPRECATION") wifi?.startScan() }
        val scanner = bt?.bluetoothLeScanner
        if (bt?.isEnabled == true && scanner != null) {
            runCatching {
                scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            }
        }
        main.postDelayed({
            stopScan()
            _wifiCount.value = runCatching { wifi?.scanResults?.map { it.BSSID }?.distinct()?.size }.getOrNull() ?: 0
            _scanning.value = false
            done(found.size + _wifiCount.value)
        }, millis)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        main.removeCallbacksAndMessages(null)
        runCatching { bt?.bluetoothLeScanner?.stopScan(scanCallback) }
        _scanning.value = false
    }

    // ---- locator ----

    private var trackedAddress: String? = null
    private val trackCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.address != trackedAddress) return
            val prev = _tracked.value?.first ?: result.rssi.toFloat()
            // Signal strength jumps around a lot, so keep a running average.
            _tracked.value = (prev + (result.rssi - prev) * 0.3f) to System.currentTimeMillis()
            found[result.device.address]?.let { found[it.address] = it.copy(rssi = result.rssi, lastSeen = System.currentTimeMillis()) }
        }
    }

    @SuppressLint("MissingPermission")
    fun track(address: String) {
        stopTracking()
        trackedAddress = address
        _tracked.value = found[address]?.let { it.rssi.toFloat() to it.lastSeen }
        val scanner = bt?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setDeviceAddress(address).build()
        runCatching {
            scanner.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), trackCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopTracking() {
        runCatching { bt?.bluetoothLeScanner?.stopScan(trackCallback) }
        trackedAddress = null
        _tracked.value = null
    }
}
