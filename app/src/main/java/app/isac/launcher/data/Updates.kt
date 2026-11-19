package app.isac.launcher.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A release published on GitHub. */
data class Release(val version: String, val page: String, val apkUrl: String?)

/**
 * Checks GitHub for a newer release, and downloads and opens it for Android to install.
 * Only the public releases page is contacted; nothing about you is sent.
 */
object Updates {
    private const val LATEST = "https://api.github.com/repos/Technically-Possible/isac-launcher/releases/latest"

    fun installed(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** True if [a] is a later version than [b], comparing numbers part by part (1.10 is after 1.9). */
    fun newer(a: String, b: String): Boolean {
        val x = a.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(LATEST).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "SHD-ISAC-launcher")
            val o = c.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val assets = o.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
            }
            Release(o.getString("tag_name").trimStart('v', 'V'), o.optString("html_url"), apk)
        }.getOrNull()
    }

    /**
     * Downloads the release's APK with Android's download manager, then opens it so Android can
     * install it over this version. Android asks you to confirm, and the first time it asks you to
     * allow SHD ISAC to install apps. Falls back to the release page if there's no APK.
     */
    fun install(context: Context, r: Release) {
        val url = r.apkUrl
        if (url == null) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val app = context.applicationContext
        val dm = app.getSystemService(DownloadManager::class.java)
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(url))
                .setTitle("SHD ISAC ${r.version}")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app, null, "SHD-ISAC-${r.version}.apk"),
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                runCatching { app.unregisterReceiver(this) }
                val uri = dm.getUriForDownloadedFile(id) ?: return
                runCatching {
                    app.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }
}
