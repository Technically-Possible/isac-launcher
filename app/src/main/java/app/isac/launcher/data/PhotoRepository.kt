package app.isac.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class Photo(val uri: Uri, val modified: Long)

/** Photos for the two frames, read from one folder the user picks with the system folder picker. */
class PhotoRepository(private val context: Context, private val prefs: Prefs) {
    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos

    private val _hasFolder = MutableStateFlow(prefs.photoTree != null)
    val hasFolder: StateFlow<Boolean> = _hasFolder

    private val bitmaps = LruCache<String, ImageBitmap>(6)

    fun setFolder(tree: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs.photoTree = tree.toString()
        _hasFolder.value = true
        bitmaps.evictAll()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val tree = prefs.photoTree?.let(Uri::parse) ?: return@withContext
        val out = ArrayList<Photo>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return@withContext
        collect(tree, rootId, depth = 0, out)
        _photos.value = out.sortedByDescending { it.modified }
    }

    private fun collect(tree: Uri, parentId: String, depth: Int, out: MutableList<Photo>) {
        if (out.size >= 2000) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        runCatching {
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val mime = c.getString(1) ?: ""
                    when {
                        mime.startsWith("image/") ->
                            out += Photo(DocumentsContract.buildDocumentUriUsingTree(tree, id), c.getLong(2))
                        mime == DocumentsContract.Document.MIME_TYPE_DIR && depth < 2 ->
                            collect(tree, id, depth + 1, out)
                    }
                }
            }
        }
    }

    /** Which photo a frame shows right now. Changes every [minutes], and [skip] moves it on by hand. */
    fun pick(frame: Int, now: Long, minutes: Int, skip: Int): Photo? {
        val list = _photos.value
        if (list.isEmpty()) return null
        val slot = if (minutes <= 0) 0L else now / (minutes * 60_000L)
        val i = ((slot * 7919 + frame * 104_729L + skip * 31L) % list.size + list.size) % list.size
        return list[i.toInt()]
    }

    suspend fun load(photo: Photo, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "${photo.uri}@$maxPx"
        bitmaps.get(key)?.let { return@withContext it }
        val bmp: Bitmap? = runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                // ImageDecoder also applies the photo's EXIF rotation.
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, photo.uri)) { dec, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxPx) dec.setTargetSampleSize(longest / maxPx)
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val longest = maxOf(bounds.outWidth, bounds.outHeight)
                val opts = BitmapFactory.Options().apply { inSampleSize = (longest / maxPx).coerceAtLeast(1) }
                context.contentResolver.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }
        }.getOrNull()
        bmp?.asImageBitmap()?.also { bitmaps.put(key, it) }
    }
}
