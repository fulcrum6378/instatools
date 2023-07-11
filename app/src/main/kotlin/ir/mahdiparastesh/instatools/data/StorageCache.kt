package ir.mahdiparastesh.instatools.data

import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.walk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Caches the paths of downloaded files for indicating that their related posts are already
 * downloaded.
 * @see ir.mahdiparastesh.instatools.list.ListPost.FlexiblePost.isStored
 */
class StorageCache {
    @Suppress("RedundantSuspendModifier")
    companion object {
        private var loading = false

        @MainThread
        fun load(c: Persistent) {
            if (loading) return
            loading = true
            CoroutineScope(Dispatchers.IO).launch {
                val stored = Stored(c.c)
                if (stored.exists()) {
                    runCatching {
                        FileInputStream(stored).use { return@use it.readBytes() }
                    }.onSuccess { data ->
                        c.m.files = Gson().fromJson<Set<String>>(String(data), Set::class.java)
                            ?.let { CopyOnWriteArraySet(it) }
                        if (c.m.files != null) loading = false
                        else checkStorage(c)
                    }.onFailure { checkStorage(c) }
                } else checkStorage(c)
            }
        }
        // CoroutineScope(Dispatchers.Main).launch { StorageCache.checkStorage(this@Main) }
        // This terrible algorithm blocked UI for more than a minute.

        private suspend fun checkStorage(c: Persistent) {
            c.m.files = CopyOnWriteArraySet()
            val paths = c.c.contentResolver.persistedUriPermissions
                .map { DocumentFile.fromTreeUri(c.c, it.uri) }
            if (paths.isNotEmpty()) {
                val mainPath = c.sPreference(Settings.spStorage)
                for (path in paths) if (path != null) (if (path.uri.toString() != mainPath) path.listFiles()
                    .toList() else path.walk().toList())
                    .filterMedia().also { c.m.files?.addAll(it) }
                saveStorageCache(c)
            }
            loading = false
        }

        suspend fun folderAdded(c: Persistent, uri: Uri) {
            if (c.m.files == null) return
            DocumentFile.fromTreeUri(c.c, uri)?.listFiles()?.toList()?.filterMedia()
                ?.also { c.m.files?.addAll(it) }
            saveStorageCache(c)
        }

        suspend fun folderRemoved(c: Persistent, uri: Uri) {
            if (c.m.files == null) return
            DocumentFile.fromTreeUri(c.c, uri)?.listFiles()?.toList()?.filterMedia()
                ?.forEach { c.m.files?.remove(it) }
            saveStorageCache(c)
        }

        suspend fun saveStorageCache(c: Persistent) {
            runCatching {
                FileOutputStream(Stored(c.c)).use {
                    it.write(Gson().toJson(c.m.files).encodeToByteArray())
                }
            }
        }

        private fun List<DocumentFile>.filterMedia() = filter {
            it.isFile && it.name?.let { n -> n.endsWith(".jpg") || n.endsWith(".mp4") } == true
        }.map { it.name!! }.toSet()
    }

    class Stored(c: Context) : File(c.cacheDir, "storage.json")
}
