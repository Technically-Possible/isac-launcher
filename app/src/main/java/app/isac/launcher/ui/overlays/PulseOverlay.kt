package app.isac.launcher.ui.overlays

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.Backdrop
import app.isac.launcher.data.Sensors
import app.isac.launcher.data.Signal
import app.isac.launcher.data.estimateMeters
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.Overlay
import app.isac.launcher.ui.components.CityBackdrop
import app.isac.launcher.ui.components.Gap
import app.isac.launcher.ui.components.Glyph
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.Panel
import app.isac.launcher.ui.components.Prompt
import app.isac.launcher.ui.components.PromptBar
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.components.topInsets
import app.isac.launcher.ui.pages.shortTime
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private fun distanceText(m: Float) = if (m < 10) "~%.1f M".format(m) else "~${m.toInt()} M"

/** Pulse: a radar of nearby devices, and a locator that follows one of them. */
@Composable
fun PulseOverlay(vm: LauncherViewModel) {
    val scanner = vm.g.scanner
    val signals by scanner.signals.collectAsStateWithLifecycle()
    val scanning by scanner.scanning.collectAsStateWithLifecycle()
    val wifi by scanner.wifiCount.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val b by vm.battery.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val alarm by vm.nextAlarm.collectAsStateWithLifecycle()
    val hPa by vm.pressure.collectAsStateWithLifecycle()
    var trackingAddress by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf(scanner.hasPermission()) }
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = scanner.hasPermission()
        if (hasPermission) vm.startPulseScan()
    }
    DisposableEffect(Unit) { onDispose { scanner.stopScan(); scanner.stopTracking() } }

    Box(Modifier.fillMaxSize().tap({})) {
        CityBackdrop(Backdrop.CITY_BLURRED)
        Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        Column(Modifier.fillMaxSize().windowInsetsPadding(topInsets())) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.Bottom) {
                Txt(if (trackingAddress == null) "PULSE" else "LOCATOR", Type.title(28.sp))
                Spacer(Modifier.weight(1f))
                Label(
                    when {
                        trackingAddress != null -> "TRACKING"
                        scanning -> "SCANNING…"
                        else -> "${signals.size + wifi} SIGNALS IN RANGE"
                    },
                    color = Ink.Orange, size = 11f,
                )
            }
            // Agent status line: time, power, core temperature, altitude, next alarm.
            val status = buildList {
                add(shortTime(now, s.use24h).uppercase())
                add("PWR ${b.percent}%")
                b.tempC?.let { add("CORE ${it.toInt()}°") }
                hPa?.let { add("ALT ~${Sensors.altitudeMeters(it).toInt()} M") }
                alarm?.let { add("ALARM ${shortTime(it, s.use24h).uppercase()}") }
            }.joinToString("  ·  ")
            Label(status, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Ink.G2, size = 10f)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasPermission -> PermissionPanel(Modifier.align(Alignment.Center)) { ask.launch(scanner.scanPermissions()) }
                    trackingAddress != null -> {
                        val target = signals.firstOrNull { it.address == trackingAddress }
                        Locator(vm, target, trackingAddress!!) { trackingAddress = null; scanner.stopTracking() }
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        Radar(signals, scanning, Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp).aspectRatio(1f))
                        if (!scanner.bluetoothOn) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp).border(1.dp, Ink.Orange.copy(alpha = 0.5f))
                                    .tap({ context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                                    .padding(10.dp),
                            ) { Txt("Bluetooth is off, so only Wi-Fi is counted. Tap to turn it on.", Type.body(13.sp, Ink.G2)) }
                        }
                        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp)) {
                            Label("DEVICES · ${signals.size}", color = Ink.Orange, size = 10.5f)
                            Spacer(Modifier.weight(1f))
                            Label("WI-FI NETWORKS · $wifi", color = Ink.G2, size = 10.5f)
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(signals, key = { it.address }) { sig ->
                                SignalRow(sig) {
                                    trackingAddress = sig.address
                                    scanner.stopScan()
                                    scanner.track(sig.address)
                                }
                            }
                        }
                    }
                }
            }
            PromptBar(
                buildList {
                    if (trackingAddress == null && hasPermission) add(Prompt("RESCAN", IsacIcons.Pulse) { vm.startPulseScan() })
                    add(Prompt("BACK", key = "‹") {
                        if (trackingAddress != null) { trackingAddress = null; scanner.stopTracking() } else vm.overlay = Overlay.None
                    })
                },
                Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun PermissionPanel(modifier: Modifier, onAllow: () -> Unit) {
    Panel(modifier.padding(24.dp).fillMaxWidth(), padding = 16.dp) {
        Label("NEARBY DEVICES")
        VGap(6.dp)
        Txt("Pulse scans for Bluetooth devices and Wi-Fi networks around you. Android needs your permission for nearby devices first. Nothing is shared or sent anywhere.", Type.body(14.sp, Ink.G2))
        VGap(12.dp)
        Row(Modifier.border(1.dp, Ink.Orange).tap(onAllow).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Label("ALLOW NEARBY DEVICES", color = Ink.White, size = 11f)
        }
    }
}

/** Radar: rings at 5, 10 and 20 metres, a sweep while scanning, a blip per device. */
@Composable
private fun Radar(signals: List<Signal>, scanning: Boolean, modifier: Modifier) {
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 360f, infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart), label = "sweepA",
    )
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = center
        fun radiusFor(m: Float) = r * sqrt((m / 20f).coerceIn(0f, 1f))
        listOf(5f, 10f, 20f).forEach { m ->
            drawCircle(Ink.Orange.copy(alpha = 0.35f), radiusFor(m), c, style = Stroke(1.dp.toPx()))
        }
        drawLine(Ink.White.copy(alpha = 0.12f), Offset(c.x - r, c.y), Offset(c.x + r, c.y))
        drawLine(Ink.White.copy(alpha = 0.12f), Offset(c.x, c.y - r), Offset(c.x, c.y + r))
        if (scanning) {
            rotate(sweep, c) {
                drawArc(
                    Brush.sweepGradient(0.80f to Color.Transparent, 1f to Ink.Orange.copy(alpha = 0.45f), center = c),
                    startAngle = -60f, sweepAngle = 60f, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
                )
                drawLine(Ink.Orange, c, Offset(c.x + r, c.y), strokeWidth = 1.5.dp.toPx())
            }
        }
        signals.forEach { sig ->
            // No direction data, so each device keeps a fixed bearing from its address.
            val angle = Math.toRadians(((sig.address.hashCode() and 0x7fffffff) % 360).toDouble())
            val rr = radiusFor(sig.meters)
            val p = Offset(c.x + (rr * cos(angle)).toFloat(), c.y + (rr * sin(angle)).toFloat())
            val known = sig.bonded || sig.name != null
            drawCircle((if (known) Ink.Orange else Ink.White).copy(alpha = 0.25f), 8.dp.toPx(), p)
            drawCircle(if (known) Ink.Orange else Ink.White, 3.5.dp.toPx(), p)
        }
        val tri = Path().apply {
            moveTo(c.x, c.y - 9.dp.toPx()); lineTo(c.x + 6.dp.toPx(), c.y + 6.dp.toPx()); lineTo(c.x - 6.dp.toPx(), c.y + 6.dp.toPx()); close()
        }
        drawPath(tri, Ink.Orange)
    }
}

@Composable
private fun SignalRow(sig: Signal, onTrack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).tap(onTrack).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(78.dp)) {
            Box(Modifier.border(1.dp, if (sig.bonded) Ink.Orange else Ink.Line).padding(horizontal = 5.dp, vertical = 2.dp)) {
                Label(sig.kind.label, color = if (sig.bonded) Ink.Orange else Ink.G2, size = 8.5f)
            }
        }
        Column(Modifier.weight(1f)) {
            Txt(sig.name ?: "Unknown device", Type.body(14.5.sp, if (sig.name != null) Ink.White else Ink.G2, FontWeight.SemiBold), maxLines = 1)
            Label("${distanceText(sig.meters)} · ${sig.rssi} DBM${if (sig.bonded) " · PAIRED" else ""}", color = Ink.G3, size = 9f)
        }
        SignalBars(((sig.rssi + 100) / 12).coerceIn(0, 5))
        Gap(12.dp)
        Label("TRACK", color = Ink.Orange, size = 9.5f)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
}

@Composable
private fun SignalBars(level: Int) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { i ->
            Box(Modifier.width(4.dp).height((5 + i * 3).dp).background(if (i < level) Ink.Orange else Ink.White.copy(alpha = 0.15f)))
        }
    }
}

/** Follows one device: closer means a fuller ring, a bigger number, and faster, stronger pings. */
@Composable
private fun Locator(vm: LauncherViewModel, target: Signal?, address: String, onStop: () -> Unit) {
    val tracked by vm.g.scanner.tracked.collectAsStateWithLifecycle()
    val history = remember { mutableStateListOf<Float>() }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tracked) { tracked?.first?.let { history.add(it); if (history.size > 12) history.removeAt(0) } }
    LaunchedEffect(Unit) { while (true) { clock = System.currentTimeMillis(); delay(500) } }

    val rssi = tracked?.first
    val lost = tracked == null || clock - tracked!!.second > 6_000
    val closeness = if (rssi == null || lost) 0f else ((rssi + 95f) / 55f).coerceIn(0f, 1f)
    val meters = rssi?.let { estimateMeters(it.toInt(), target?.txPower) }
    val trend = if (history.size >= 6) {
        val half = history.size / 2
        history.takeLast(half).average() - history.take(half).average()
    } else 0.0
    val word = when {
        lost -> "SIGNAL LOST"
        trend > 2 -> "WARMER"
        trend < -2 -> "COLDER"
        else -> "HOLDING"
    }

    // Haptic pings: about every 1.6 s when far, down to 0.15 s when right next to it.
    var level by remember { mutableFloatStateOf(0f) }
    level = closeness
    LaunchedEffect(lost) {
        if (lost) return@LaunchedEffect
        while (true) {
            vm.g.haptics.ping(level)
            delay((1_600 - 1_450 * level).toLong())
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Txt(target?.name ?: "Unknown device", Type.title(20.sp), maxLines = 1)
        Label(address, color = Ink.G3, size = 9.5f)
        VGap(12.dp)
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 14.dp.toPx()
                val r = size.minDimension / 2 - sw
                val lit = (closeness * 24).toInt()
                repeat(24) { i ->
                    drawArc(
                        if (i < lit) (if (closeness > 0.75f) Ink.Orange else Ink.Orange.copy(alpha = 0.75f)) else Ink.White.copy(alpha = 0.10f),
                        startAngle = -90f + i * 15f + 1.5f, sweepAngle = 12f, useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r), style = Stroke(sw),
                    )
                }
                drawCircle(Ink.Orange.copy(alpha = 0.25f), r * 0.62f, style = Stroke(1.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(if (lost || meters == null) "—" else distanceText(meters), Type.big(46.sp))
                VGap(4.dp)
                Label(word, color = if (lost) Ink.Crit else Ink.Orange, size = 13f)
                rssi?.let { if (!lost) Label("${it.toInt()} DBM", color = Ink.G3, size = 9.5f) }
            }
        }
        VGap(16.dp)
        Txt(
            "Move around slowly and watch the ring. Distance is a rough guess from signal strength. Some devices change their Bluetooth address every few minutes; if the signal is lost, run a new Pulse.",
            Type.body(13.sp, Ink.G2),
        )
        VGap(12.dp)
        Row(Modifier.border(1.dp, Ink.Orange).tap(onStop).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Label("STOP TRACKING", color = Ink.White, size = 11f)
        }
    }
}

// ------------------------------------------------------------------ voice

/**
 * "ISAC, …": listens for one command, shows what it heard, and carries it out.
 * Uses ISAC's offline voice pack, or the phone's speech service if it has a proper one.
 */
@Composable
fun VoiceOverlay(vm: LauncherViewModel, tabs: List<String>) {
    var heard by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var listening by remember { mutableStateOf(false) }
    val offline = vm.g.offlineVoice
    var needsPack by remember { mutableStateOf(!offline.installed && !vm.g.voice.usable) }
    var downloading by remember { mutableStateOf<Float?>(null) }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun handle(text: String?) {
        listening = false
        if (text.isNullOrBlank()) {
            reply = "I didn't catch that. Hold Pulse and try again."
        } else {
            heard = text
            reply = vm.runCommand(text, tabs)
        }
    }
    fun begin() {
        vm.g.haptics.tick()
        if (offline.installed) {
            listening = true
            scope.launch {
                val model = offline.load()
                if (model == null) { listening = false; reply = "The voice pack didn't load. Try downloading it again in Settings." }
                else offline.listen(model, onPartial = { heard = it }, onResult = ::handle)
            }
        } else {
            listening = true
            vm.g.voice.start(onPartial = { heard = it }, onLevel = { level = it }, onResult = ::handle)
        }
    }
    fun downloadPack() {
        downloading = 0f
        scope.launch {
            val ok = offline.download { p -> downloading = p }
            downloading = null
            if (ok) { needsPack = false; begin() } else reply = "The download didn't finish. Check your connection and try again."
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else reply = "ISAC needs the microphone to hear commands."
    }
    LaunchedEffect(Unit) {
        if (needsPack) return@LaunchedEffect
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) begin() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    // The offline engine doesn't report loudness, so animate the bars while it listens.
    LaunchedEffect(listening, offline.installed) {
        if (!offline.installed) return@LaunchedEffect
        var t = 0f
        while (listening) { t += 0.35f; level = 0.35f + 0.3f * kotlin.math.abs(sin(t)); delay(80) }
    }
    LaunchedEffect(reply) {
        if (reply != null) {
            delay(2_800)
            if (vm.overlay == Overlay.Voice) vm.overlay = Overlay.None
        }
    }
    DisposableEffect(Unit) { onDispose { vm.g.voice.stop(); offline.stop() } }

    if (needsPack) {
        Scrim({ if (downloading == null) vm.overlay = Overlay.None }) {
            Panel(Modifier.align(Alignment.Center).padding(horizontal = 20.dp).fillMaxWidth().tap({}), padding = 18.dp, fill = Ink.GlassDeep) {
                Label("ISAC · VOICE PACK")
                VGap(8.dp)
                Txt("Your phone doesn't have a speech recogniser, so ISAC uses its own. It works offline: what you say never leaves the phone.", Type.body(14.sp, Ink.G2))
                VGap(6.dp)
                Txt("The English voice pack is a one-time ${app.isac.launcher.isac.OfflineVoice.MODEL_MB} MB download from alphacephei.com (Vosk, open source).", Type.body(13.sp, Ink.G3))
                VGap(14.dp)
                val p = downloading
                if (p != null) {
                    Label(if (p < 0.9f) "DOWNLOADING · ${(p / 0.9f * 100).toInt()}%" else "UNPACKING…", color = Ink.Orange, size = 11f)
                    VGap(6.dp)
                    app.isac.launcher.ui.components.RollBar(p, Ink.Orange)
                } else {
                    reply?.let { Txt(it, Type.body(13.sp, Ink.Crit)); VGap(10.dp) }
                    Row(Modifier.border(1.dp, Ink.Orange).tap({ reply = null; downloadPack() }).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Label("DOWNLOAD VOICE PACK", color = Ink.White, size = 11f)
                    }
                }
            }
        }
        return
    }

    Scrim({ vm.overlay = Overlay.None }) {
        Panel(Modifier.align(Alignment.Center).padding(horizontal = 20.dp).fillMaxWidth().tap({}), padding = 18.dp, fill = Ink.GlassDeep) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(if (listening) "ISAC · LISTENING" else "ISAC")
                Spacer(Modifier.weight(1f))
                if (listening) LevelBars(level)
            }
            VGap(12.dp)
            Txt(if (heard.isEmpty()) "…" else "“$heard”", Type.body(20.sp, Ink.White, FontWeight.SemiBold))
            reply?.let {
                VGap(10.dp)
                Txt(it, Type.body(15.sp, Ink.Orange))
            }
            if (reply == null && heard.isEmpty()) {
                VGap(12.dp)
                Txt("Try “open Signal”, “timer for ten minutes”, “alarm at 7:30”, “torch on”, “weather” or “take me home”.", Type.body(13.sp, Ink.G2))
            }
        }
    }
}

@Composable
private fun LevelBars(level: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(10) { i ->
            val h = (4 + 16 * level * (0.5f + 0.5f * sin(i * 1.3f + level * 9f))).coerceIn(3f, 20f)
            Box(Modifier.width(3.dp).height(h.dp).background(Ink.Orange))
        }
    }
}
