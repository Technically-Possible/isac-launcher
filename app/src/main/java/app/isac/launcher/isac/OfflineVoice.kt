package app.isac.launcher.isac

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * ISAC's own offline speech recognition (Vosk), for phones without a speech service, like GrapheneOS.
 * The English voice pack is downloaded once; after that, listening happens entirely on the phone.
 */
class OfflineVoice(private val context: Context) {
    private val root = File(context.filesDir, "vosk")
    private val modelDir = File(root, "model")
    private var model: Model? = null
    private var service: SpeechService? = null

    val installed: Boolean get() = File(modelDir, "am").isDirectory && File(modelDir, "conf").isDirectory

    /** Downloads and unpacks the voice pack. [progress] goes from 0 to 1. Returns false on failure. */
    suspend fun download(progress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val zip = File(context.cacheDir, "vosk-model.zip")
        runCatching {
            val c = URL(MODEL_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "IsacLauncher/0.1")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: 41_205_931L
            c.inputStream.use { input ->
                zip.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        progress((done.toFloat() / total * 0.9f).coerceAtMost(0.9f))
                    }
                }
            }
            c.disconnect()
            unpack(zip)
            progress(1f)
            installed
        }.getOrElse { false }.also { zip.delete() }
    }

    /** Unzips into the model folder, dropping the archive's top-level folder and refusing paths that escape it. */
    private fun unpack(zip: File) {
        modelDir.deleteRecursively()
        modelDir.mkdirs()
        val base = modelDir.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val rel = e.name.substringAfter('/', "")
                if (rel.isEmpty()) continue
                val target = File(modelDir, rel)
                if (!target.canonicalPath.startsWith(base)) continue
                if (e.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { z.copyTo(it) }
                }
            }
        }
    }

    /** Loads the voice pack into memory the first time it's needed (about a second). */
    suspend fun load(): Model? = withContext(Dispatchers.IO) {
        model ?: runCatching {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Model(modelDir.absolutePath)
        }.getOrNull()?.also { model = it }
    }

    /** Listens for one command and reports the words heard, or null if nothing was understood. */
    fun listen(model: Model, onPartial: (String) -> Unit, onResult: (String?) -> Unit) {
        stop()
        val rec = Recognizer(model, 16_000f)
        var finished = false
        fun finish(text: String?) {
            if (finished) return
            finished = true
            stop()
            onResult(text?.takeIf { it.isNotBlank() })
        }
        val s = SpeechService(rec, 16_000f)
        service = s
        s.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                val p = hypothesis?.let { runCatching { JSONObject(it).optString("partial") }.getOrNull() }
                if (!p.isNullOrBlank()) onPartial(p)
            }
            override fun onResult(hypothesis: String?) {
                val t = hypothesis?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() }
                if (!t.isNullOrBlank()) finish(t)
            }
            override fun onFinalResult(hypothesis: String?) {
                finish(hypothesis?.let { runCatching { JSONObject(it).optString("text") }.getOrNull() })
            }
            override fun onError(exception: Exception?) = finish(null)
            override fun onTimeout() = finish(null)
        }, 8_000)
    }

    fun stop() {
        service?.runCatching { stop(); shutdown() }
        service = null
    }

    companion object {
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        const val MODEL_MB = 41
    }
}
