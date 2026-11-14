package app.isac.launcher

import android.app.Application
import android.content.Context
import app.isac.launcher.data.AppRepository
import app.isac.launcher.data.CalendarRepository
import app.isac.launcher.data.IconCache
import app.isac.launcher.data.PhotoRepository
import app.isac.launcher.data.Prefs
import app.isac.launcher.data.UsageRepository
import app.isac.launcher.data.WeatherRepository
import app.isac.launcher.data.WidgetHostManager
import app.isac.launcher.data.Exploration
import app.isac.launcher.data.PlacesRepository
import app.isac.launcher.data.Directives
import app.isac.launcher.data.MediaRepository
import app.isac.launcher.data.PulseScanner
import app.isac.launcher.isac.Haptics
import app.isac.launcher.isac.VoiceInput
import app.isac.launcher.isac.OfflineVoice
import app.isac.launcher.isac.Isac
import app.isac.launcher.isac.Torch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class IsacApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }
}

/** Long-lived singletons shared by the activity and view model. */
class Graph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = Prefs(app)
    val apps = AppRepository(app, scope)
    val icons = IconCache(app)
    val usage = UsageRepository(app)
    val weather = WeatherRepository(app, prefs)
    val calendar = CalendarRepository(app)
    val photos = PhotoRepository(app, prefs)
    val widgets = WidgetHostManager(app, prefs)
    val isac = Isac(app)
    val torch = Torch(app)
    val exploration = Exploration(app, prefs, scope)
    val places = PlacesRepository(app, exploration, scope).also { exploration.places = it }
    val directives = Directives(prefs, exploration)
    val media = MediaRepository(app)
    val scanner = app.let { PulseScanner(it) }
    val voice = app.let { VoiceInput(it) }
    val offlineVoice = OfflineVoice(app)
    val haptics = Haptics(app).also { h ->
        h.enabled = prefs.settings.value.haptics
    }
}

val Context.graph: Graph get() = (applicationContext as IsacApp).graph
