package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.annotation.MainThread
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.walk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet

class StorageCache {
    companion object {
        private var loading = false

        @MainThread
        fun load(c: Persistent) {
            if (loading) return
            loading = true
            val stored = Stored(c.c)
            CoroutineScope(Dispatchers.IO).launch {
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
                for (path in paths) path?.walk()?.forEach {
                    if (it.isFile && it.name != null &&
                        (it.name!!.endsWith(".jpg") || it.name!!.endsWith(".mp4"))
                    ) c.m.files?.add(it.name!!)
                }
                saveStorageCache(c)
            }
            loading = false
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun saveStorageCache(c: Persistent) {
            runCatching {
                FileOutputStream(Stored(c.c)).use {
                    it.write(Gson().toJson(c.m.files).encodeToByteArray())
                }
            }
        }
    }

    class Stored(c: Context) : File(c.cacheDir, "storage.json")
}
