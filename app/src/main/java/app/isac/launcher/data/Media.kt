package app.isac.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val app: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val controller: MediaController,
)

/**
 * What's playing (YT Music, podcasts, anything with a media session), with play, pause and skip.
 * Android only shares media sessions with apps that have notification access, which the
 * notification counters already use.
 */
class MediaRepository(private val context: Context) {
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now

    private var controllers: List<MediaController> = emptyList()
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        watch(list.orEmpty())
    }

    /** Starts following media sessions, if notification access is on. Safe to call repeatedly. */
    fun start() {
        if (listening || !Badges.hasAccess(context)) return
        val component = Badges.component(context)
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            watch(msm.getActiveSessions(component))
            listening = true
        }
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        listening = false
    }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { runCatching { it.registerCallback(callback, main) } }
        publish()
    }

    private fun publish() {
        val pick = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED }
        val md = pick?.metadata
        if (pick == null || md == null) { _now.value = null; return }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) { _now.value = null; return }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val bmp: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val appLabel = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pick.packageName, 0)).toString()
        }.getOrDefault(pick.packageName)
        _now.value = NowPlaying(title, artist, appLabel, bmp?.asImageBitmap(), pick.playbackState?.state == PlaybackState.STATE_PLAYING, pick)
    }

    fun playPause() {
        val c = _now.value?.controller ?: return
        if (_now.value?.playing == true) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = _now.value?.controller?.transportControls?.skipToNext()
    fun previous() = _now.value?.controller?.transportControls?.skipToPrevious()

    fun open() {
        runCatching { _now.value?.controller?.sessionActivity?.send() }
    }
}
