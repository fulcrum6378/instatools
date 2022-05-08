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
import kotlinx.coroutines.withContext
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
            if (stored.exists()) CoroutineScope(Dispatchers.IO).launch {
                var data: ByteArray? = null
                runCatching {
                    FileInputStream(stored).use { data = it.readBytes() }
                }.onSuccess {
                    if (data == null) {
                        checkStorage(c); return@onSuccess; }
                    c.m.files = CopyOnWriteArraySet(
                        Gson().fromJson<Set<String>>(String(data!!), Set::class.java)
                    )
                    loading = false
                }.onFailure { withContext(Dispatchers.Main) { checkStorage(c) } }
            } else checkStorage(c)
        }

        @MainThread
        private fun checkStorage(c: Persistent) {
            c.m.files = CopyOnWriteArraySet()
            val paths = c.c.contentResolver.persistedUriPermissions
                .map { DocumentFile.fromTreeUri(c.c, it.uri) }
            if (paths.isNotEmpty()) CoroutineScope(Dispatchers.IO).launch {
                for (path in paths) path?.walk()?.forEach {
                    if (it.isFile && it.name != null &&
                        (it.name!!.endsWith(".jpg") || it.name!!.endsWith(".mp4"))
                    ) c.m.files?.add(it.name!!)
                }
            }.invokeOnCompletion {
                CoroutineScope(Dispatchers.IO).launch { saveStorageCache(c) }
                loading = false
            } else loading = false
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
