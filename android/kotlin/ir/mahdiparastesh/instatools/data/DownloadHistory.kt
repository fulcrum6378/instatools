package ir.mahdiparastesh.instatools.data

import android.net.Uri
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
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
 *
 * @see ir.mahdiparastesh.instatools.list.ListPost
 * @see ir.mahdiparastesh.instatools.list.ListStory
 */
class DownloadHistory(private val file: File) {

    private var list: CopyOnWriteArraySet<String>? = null
    private var loading = false

    /** Loads data from the storage only if it is not loaded yet. */
    @MainThread
    fun load(c: InstaTools) {
        if (list != null || loading) return
        loading = true
        CoroutineScope(Dispatchers.IO).launch {
            if (file.exists()) {
                runCatching {
                    FileInputStream(file).use {
                        return@use it.reader(Charsets.US_ASCII).readLines()
                    }
                }.onSuccess { data ->
                    list = CopyOnWriteArraySet()
                    list!!.addAll(data)

                    if (!list.isNullOrEmpty()) loading = false
                    else scanStorage(c)
                }.onFailure {
                    if (BuildConfig.DEBUG) throw it
                    scanStorage(c)
                }
            } else scanStorage(c)
        }
    }

    /** Scans the download folder to see what files are already downloaded. */
    @WorkerThread
    private fun scanStorage(c: InstaTools) {

        // migration from the old JSON method
        val oldJsonDownloadHistory = File(c.cacheDir, "download_history.json")
        if (oldJsonDownloadHistory.exists()) {
            list = CopyOnWriteArraySet(
                FileInputStream(oldJsonDownloadHistory).use {
                    Json.decodeFromString<List<String>>(String(it.readBytes()))
                }
            )
            save()
            oldJsonDownloadHistory.delete()
            loading = false
            return
        }

        list = CopyOnWriteArraySet()
        val paths = c.contentResolver.persistedUriPermissions
            .map { DocumentFile.fromTreeUri(c, it.uri) }
        if (paths.isNotEmpty()) {
            val mainPath = c.sPreference(Settings.spStorage)
            for (path in paths) if (path != null) (if (path.uri.toString() != mainPath) path.listFiles()
                .toList() else path.walk().toList())
                .filterMedia().also { list?.addAll(it) }
            save()
        }
        loading = false
    }

    /** Quickly registers a file that is downloaded. */
    @WorkerThread
    fun add(fileName: String) {
        if (list.isNullOrEmpty()) return
        if (!list!!.add(fileName)) return
        FileOutputStream(file, true).use { fos ->
            fos.write(
                "${if (list!!.isEmpty()) "" else "\n"}$fileName"
                    .toByteArray(Charsets.US_ASCII)
            )
        }
    }

    /** Rewrites the entire history. */
    @WorkerThread
    private fun save() {
        if (list.isNullOrEmpty()) return
        FileOutputStream(file).use { fos ->
            fos.write(list!!.toList().joinToString("\n").toByteArray(Charsets.US_ASCII))
        }
    }

    /*@MainThread
    fun clear() {
        list = null
        CoroutineScope(Dispatchers.IO).launch { save() }
    }*/

    /** Scans a newly added download folder. */
    @WorkerThread
    fun folderAdded(c: InstaTools, uri: Uri) {
        if (list == null) return
        DocumentFile.fromTreeUri(c, uri)?.listFiles()?.toList()?.filterMedia()
            ?.also { list?.addAll(it) }
        save()
    }

    fun anyStartsWith(q: String): Boolean {
        if (list.isNullOrEmpty()) return false
        //Log.d("ESPINELA", q)
        for (s in list)
            if (s.startsWith(q))
                return true
        return false
    }

    fun anyContains(q: String): Boolean {
        if (list.isNullOrEmpty()) return false
        //Log.d("ESPINELA", q)
        for (s in list)
            if (s.contains(q))
                return true
        return false
    }

    fun isEmpty(): Boolean = list.isNullOrEmpty()

    /** Unregisters all files with a removed download folder. */
    @WorkerThread
    fun folderRemoved(c: InstaTools, uri: Uri) {
        if (list == null) return
        DocumentFile.fromTreeUri(c, uri)?.listFiles()?.toList()?.filterMedia()
            ?.forEach { list?.remove(it) }
        save()
    }

    /** Filters IG media out of a list of files with a folder. */
    @WorkerThread
    private fun List<DocumentFile>.filterMedia() = filter {
        it.isFile && it.name?.let { n ->
            n.endsWith(".mp4") ||
                n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".heic")
        } == true
    }.map { it.name!! }.toSet()
}
