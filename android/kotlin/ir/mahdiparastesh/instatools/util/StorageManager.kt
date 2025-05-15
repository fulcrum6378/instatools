package ir.mahdiparastesh.instatools.util

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.StatFs
import androidx.annotation.WorkerThread
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.toBytes
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Pickle
import java.io.File

/**
 * A dependency of [InstaTools] which manages files inside the private internal storage.
 */
class StorageManager(private val c: InstaTools) {

    /** Deletes a [SharedPreferences] XML file that belongs to a specific [Account] by its ID. */
    fun deleteSp(accountId: String = c.acc!!.id.toString()) {
        File(c.getDir("shared_prefs", MODE_PRIVATE), "$accountId.xml")
            .apply { if (exists()) delete() }
    }

    /** Deletes all [Pickle]s that belong to a specific [Account] by its ID. */
    fun deletePickles(accountId: String = c.acc!!.id.toString()) {
        File(c.filesDir, Pickle.DIR_PREFIX + accountId).deleteRecursively()
        File(c.cacheDir, Pickle.DIR_PREFIX + accountId).deleteRecursively()
    }

    @WorkerThread
    fun deleteExpiredCachePickles(acc: Long) {
        val now = System.currentTimeMillis()
        for (pickleType in Pickle.Type.entries) {
            if (!pickleType.isApiCache) continue
            val branch = Pickle.branch(c.cacheDir, pickleType, acc)
            if (!branch.exists()) continue

            for (leaf in branch.listFiles()!!)
                if ((now - leaf.lastModified()) >= pickleType.lifespan)
                    leaf.delete()
        }
    }


    @WorkerThread
    fun clearCache(subdir: CacheSubdir) {
        File(c.cacheDir, subdir.dirName).deleteRecursively()
    }

    @WorkerThread
    fun clearCacheIfNecessary(subdir: CacheSubdir): Boolean {
        if (cacheSize() <= c.gsp.getLong(Settings.spCacheLimit, defaultCacheLimit()))
            return false
        clearCache(subdir)
        return true
    }

    @WorkerThread
    fun cacheSize() = c.cacheDir.walk().sumOf { it.length() } - 4096L

    fun defaultCacheLimit(): Long = c.getExternalFilesDir(null)?.let {
        val minie = c.resources.getInteger(R.integer.stCacheMin)
        val maxie = c.resources.getInteger(R.integer.stCacheMaxNominal) + minie
        val stat = StatFs(it.path)
        var ret = (stat.blockSizeLong * stat.availableBlocksLong) / 275L
        if (ret < minie.toBytes()) ret = minie.toBytes()
        if (ret > maxie.toBytes()) ret = maxie.toBytes()
        return ret
    } ?: Settings.defSpCacheLimit


    enum class CacheSubdir(val dirName: String) {

        /** Cached images of Glide */
        IMAGE("image_manager_disk_cache"),

        /** WebView cache */
        WEB("WebView")
    }
}
