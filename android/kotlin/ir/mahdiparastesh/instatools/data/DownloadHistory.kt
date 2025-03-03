package ir.mahdiparastesh.instatools.data

import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.util.walk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Caches paths of downloaded files in order to show to the user that their related posts are
 * already downloaded.
 * @see [ir.mahdiparastesh.instatools.list.ListPost]
 */
object DownloadHistory {
    private var loading = false

    @MainThread
    fun load(c: InstaTools) {
        if (loading) return
        loading = true
        CoroutineScope(Dispatchers.IO).launch {
            val stored = Stored(c)
            if (stored.exists()) {
                runCatching {
                    FileInputStream(stored).use { return@use it.readBytes() }
                }.onSuccess { data ->
                    c.downloadHistory =
                        CopyOnWriteArraySet(Json.decodeFromString<List<String>>(String(data)))
                    if (c.downloadHistory != null) loading = false
                    else checkStorage(c)
                }.onFailure {
                    if (BuildConfig.DEBUG) throw it
                    checkStorage(c)
                }
            } else checkStorage(c)
        }
    }

    private fun checkStorage(c: InstaTools) {
        c.downloadHistory = CopyOnWriteArraySet()
        val paths = c.contentResolver.persistedUriPermissions
            .map { DocumentFile.fromTreeUri(c, it.uri) }
        if (paths.isNotEmpty()) {
            val mainPath = c.sPreference(Settings.spStorage)
            for (path in paths) if (path != null) (if (path.uri.toString() != mainPath) path.listFiles()
                .toList() else path.walk().toList())
                .filterMedia().also { c.downloadHistory?.addAll(it) }
            saveCache(c)
        }
        loading = false
    }

    fun folderAdded(c: InstaTools, uri: Uri) {
        if (c.downloadHistory == null) return
        DocumentFile.fromTreeUri(c, uri)?.listFiles()?.toList()?.filterMedia()
            ?.also { c.downloadHistory?.addAll(it) }
        saveCache(c)
    }

    fun folderRemoved(c: InstaTools, uri: Uri) {
        if (c.downloadHistory == null) return
        DocumentFile.fromTreeUri(c, uri)?.listFiles()?.toList()?.filterMedia()
            ?.forEach { c.downloadHistory?.remove(it) }
        saveCache(c)
    }

    fun saveCache(c: InstaTools) {
        if (c.downloadHistory.isNullOrEmpty()) return
        FileOutputStream(Stored(c)).use {
            it.write(Json.encodeToString(c.downloadHistory?.toList()).encodeToByteArray())
        }
    }

    private fun List<DocumentFile>.filterMedia() = filter {
        it.isFile && it.name?.let { n ->
            n.endsWith(".mp4") ||
                n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".heic")
        } == true
    }.map { it.name!! }.toSet()

    class Stored(c: Context) : File(c.cacheDir, "download_history.json")
}
