package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.util.Utils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Caches data models in order to reduce the number of API requests.
 * Used as an object-oriented data storage as well!
 */
class Pickle(root: File, acc: Long, val type: Type, id: String?) {
    val branch: File
    val file: File

    init {
        val tree = File(root, "pickle_$acc")
        branch = if (type.singleFile) tree else File(tree, type.name.lowercase())
        file = File(branch, "${if (type.singleFile) type.name.lowercase() else id}.json")
    }

    inline fun <reified DATA> save(data: DATA) {
        if (!branch.exists()) branch.mkdirs()
        FileOutputStream(file).use {
            it.write(Api.json.encodeToString(data).encodeToByteArray())
        }
    }

    inline fun <reified DATA> restore(): DATA? {
        if (!file.exists()) return null
        val lifespan = type.lifespan()
        if (lifespan != 0L && (Utils.now() - file.lastModified()) >= lifespan) return null
        return try {
            Api.json.decodeFromString<DATA>(
                FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }
            )
        } catch (e: Exception) {
            throw e
            //null
        }
    }

    enum class Type(
        /** set it to zero to make it never expire */
        val lifespanInDays: Float,
        val singleFile: Boolean = true,
    ) {
        // queues
        DOWNLOAD_LIST(0f),
        COMMAND_LIST(7f),

        // caches
        SAVED(1f),
        PROFILE(2f, false),
        POSTS(3f, false),
        STORY(1f, false),
        HIGHLIGHTS(7f, false),
        TAGGED(7f, false);

        fun lifespan(): Long = (lifespanInDays * 86400000f).toLong()
    }
}
