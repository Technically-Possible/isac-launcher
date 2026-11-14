package app.isac.launcher.ui.pages

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.isac.launcher.data.ExpeditionService
import app.isac.launcher.data.ExplorationTracker
import app.isac.launcher.data.Place
import kotlinx.coroutines.launch
import app.isac.launcher.ui.LauncherViewModel
import app.isac.launcher.ui.components.Gap
import app.isac.launcher.ui.components.Glyph
import app.isac.launcher.ui.components.Label
import app.isac.launcher.ui.components.Panel
import app.isac.launcher.ui.components.Txt
import app.isac.launcher.ui.components.VGap
import app.isac.launcher.ui.components.tap
import app.isac.launcher.ui.theme.Ink
import app.isac.launcher.ui.theme.IsacIcons
import app.isac.launcher.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** MAP: the fog-of-war exploration map, your stats, cities unlocked, and Expedition mode. */
@Composable
fun MapPage(vm: LauncherViewModel, active: Boolean, modifier: Modifier = Modifier) {
    val ex = vm.g.exploration
    val cells by ex.cellCount.collectAsStateWithLifecycle()
    val cities by ex.cities.collectAsStateWithLifecycle()
    val km by ex.distanceKm.collectAsStateWithLifecycle()
    val expedition by ex.expeditionActive.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    var showCities by remember { mutableStateOf(false) }
    var showAccess by remember { mutableStateOf(false) }
    var tapped by remember { mutableStateOf<Place?>(null) }
    val found by ex.caches.collectAsStateWithLifecycle()
    val places by vm.g.places.places.collectAsStateWithLifecycle()
    var recenter by remember { mutableStateOf(0) }
    val context = LocalContext.current

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ExplorationTracker.hasLocation(context)) { ExplorationTracker.start(context); vm.revealHere() }
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ExpeditionService.start(context) }
    fun toggleExpedition() {
        when {
            expedition -> ExpeditionService.stop(context)
            !ExplorationTracker.hasLocation(context) ->
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            Build.VERSION.SDK_INT >= 33 -> askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> ExpeditionService.start(context)
        }
    }

    Box(modifier.fillMaxSize()) {
        if (active) ExplorationMap(vm, recenter, showAccess, Modifier.fillMaxSize()) { tapped = it; showCities = false }
        else Box(Modifier.fillMaxSize().background(Color(0xFF1A2129)))

        // Stats across the top of the map.
        Panel(Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(), padding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("EXPLORATION")
                Spacer(Modifier.weight(1f))
                Label("LEVEL $level", color = Ink.White, size = 11f)
            }
            VGap(6.dp)
            Box(Modifier.fillMaxWidth().height(3.dp).background(Ink.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(ex.levelProgress()).height(3.dp).background(Ink.Orange))
            }
            VGap(8.dp)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Stat("%.1f KM²".format(Locale.UK, ex.areaKm2()), "UNCOVERED")
                Stat("%.0f KM".format(Locale.UK, km), "TRAVELLED")
                Stat("${cities.size}", "CITIES")
                Stat("${found.size}/${places.count { it.landmark }}", "CACHES")
                Stat("${ex.xp}", "XP")
            }
            val precise = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!precise) {
                VGap(8.dp)
                Txt(
                    if (ExplorationTracker.hasLocation(context))
                        "Location is set to approximate, which is only accurate to a few km. Cities still unlock, but the fog only clears with precise location."
                    else "Allow location to start mapping. The fog clears as you move, using precise location.",
                    Type.body(12.5.sp, Ink.G2),
                )
                VGap(6.dp)
                Row(
                    Modifier.border(1.dp, Ink.Orange).tap({
                        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }).padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Label("USE PRECISE LOCATION", color = Ink.White, size = 10f) }
            }
        }

        // Cities list, opened from the bottom bar.
        if (showCities) {
            Panel(
                Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 70.dp).fillMaxWidth(),
                padding = 12.dp, fill = Ink.GlassDeep,
            ) {
                Label("CITIES UNLOCKED · ${cities.size}")
                VGap(8.dp)
                if (cities.isEmpty()) {
                    Txt("Visit a new town or city and it unlocks here, worth 150 XP.", Type.body(13.sp, Ink.G2))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(cities.sortedByDescending { it.firstVisit }) { c ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(Ink.Orange))
                                Gap(10.dp)
                                Txt(c.name.uppercase(), Type.title(15.sp), Modifier.weight(1f), maxLines = 1)
                                Label(
                                    "${c.country} · " + Instant.ofEpochMilli(c.firstVisit).atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)),
                                    color = Ink.G3, size = 9.5f,
                                )
                            }
                        }
                    }
                }
            }
        }

        // A tapped landmark or accessible place.
        tapped?.let { p ->
            Panel(
                Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 70.dp).fillMaxWidth().tap({ tapped = null }),
                padding = 12.dp, fill = Ink.GlassDeep,
            ) {
                Label(
                    when {
                        p.landmark && p.id in found -> "SHD CACHE · RECOVERED"
                        p.landmark -> "SHD CACHE · GET WITHIN 60 M TO RECOVER"
                        p.toilet -> "ACCESSIBLE TOILET"
                        else -> "ACCESSIBLE PLACE"
                    },
                )
                VGap(4.dp)
                Txt(p.name.uppercase(), Type.title(18.sp), maxLines = 2)
                VGap(2.dp)
                val access = when (p.access) {
                    "yes", "designated" -> "Wheelchair accessible"
                    "limited" -> "Partly wheelchair accessible"
                    else -> null
                }
                Txt(listOfNotNull(p.kind.replaceFirstChar(Char::uppercase), access).joinToString(" · "), Type.body(13.sp, Ink.G2))
                VGap(2.dp)
                Label("FROM OPENSTREETMAP · CHECK BEFORE YOU RELY ON IT", color = Ink.G3, size = 8.5f)
            }
        }

        // Bottom controls.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapButton(if (expedition) "END EXPEDITION" else "START EXPEDITION", expedition, Modifier.weight(1f)) { toggleExpedition() }
            MapButton("ACCESS", showAccess) { showAccess = !showAccess }
            MapButton("CITIES", showCities) { showCities = !showCities; tapped = null }
            MapButton("◎", false) { recenter++ }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Txt(value, Type.title(16.sp))
        Label(label, color = Ink.G2, size = 8.5f)
    }
}

@Composable
private fun MapButton(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .background(if (on) Ink.Orange else Ink.GlassDeep)
            .border(1.dp, Ink.Orange)
            .tap(onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Label(text, color = if (on) Ink.OnOrange else Ink.White, size = 11f) }
}

/** The MapLibre map, kept in step with the screen's lifecycle and redrawn when the fog changes. */
@Composable
private fun ExplorationMap(vm: LauncherViewModel, recenter: Int, showAccess: Boolean, modifier: Modifier, onTap: (Place?) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ex = vm.g.exploration
    val version by ex.version.collectAsStateWithLifecycle()
    val base by vm.base.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.setAttributionMargins(16, 0, 0, 230)
            m.setStyle(Style.Builder().fromJson(ISAC_MAP_STYLE.replace("#FF6D10", vm.settings.value.accent.hex))) { s -> style = s }
            m.addOnMapClickListener { point ->
                val hit = m.queryRenderedFeatures(m.projection.toScreenLocation(point), "caches", "access").firstOrNull()
                onTap(hit?.getNumberProperty("pid")?.toLong()?.let { vm.g.places.get(it) })
                hit != null
            }
            // Fetch landmarks for wherever the map settles.
            m.addOnCameraIdleListener {
                val c = m.cameraPosition
                val t = c.target
                if (t != null && c.zoom >= 12.5) scope.launch { vm.g.places.ensureAround(t.latitude, t.longitude) }
            }
            val fix = vm.g.prefs.lastFix ?: base
            if (fix != null) m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fix.first, fix.second), 14.0))
        }
    }
    // Fog of war, redrawn whenever new ground is uncovered.
    LaunchedEffect(style, version) {
        val s = style ?: return@LaunchedEffect
        val (fog, edges) = withContext(Dispatchers.Default) { ex.fogGeoJson() }
        s.getSourceAs<GeoJsonSource>("fog")?.setGeoJson(fog)
        s.getSourceAs<GeoJsonSource>("edges")?.setGeoJson(edges)
        vm.g.prefs.lastFix?.let { (la, lo) -> s.getSourceAs<GeoJsonSource>("me")?.setGeoJson(pointJson(la, lo)) }
    }
    val places by vm.g.places.places.collectAsStateWithLifecycle()
    val found by ex.caches.collectAsStateWithLifecycle()
    LaunchedEffect(style, places, found) {
        val s = style ?: return@LaunchedEffect
        val (caches, access) = withContext(Dispatchers.Default) { vm.g.places.cachesGeoJson(found) to vm.g.places.accessGeoJson() }
        s.getSourceAs<GeoJsonSource>("caches")?.setGeoJson(caches)
        s.getSourceAs<GeoJsonSource>("access")?.setGeoJson(access)
    }
    LaunchedEffect(style, showAccess) {
        val s = style ?: return@LaunchedEffect
        val v = if (showAccess) "visible" else "none"
        listOf("access", "access-label").forEach { id -> s.getLayer(id)?.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(v)) }
    }
    LaunchedEffect(style, base) {
        val s = style ?: return@LaunchedEffect
        base?.let { (la, lo) -> s.getSourceAs<GeoJsonSource>("base")?.setGeoJson(pointJson(la, lo)) }
    }
    LaunchedEffect(recenter) {
        if (recenter == 0) return@LaunchedEffect
        val fix = vm.g.prefs.lastFix ?: base ?: return@LaunchedEffect
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fix.first, fix.second), 14.5))
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
