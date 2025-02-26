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
@Suppress("RedundantSuspendModifier")
class Pickle(root: File, acc: Long, type: Type, id: String?) {
    val branch: File
    val file: File
    val lifespan: Long

    init {
        val tree = File(root, "pickle_$acc")
        branch = if (type.singleFile) tree else File(tree, type.name.lowercase())
        file = File(branch, "${if (type.singleFile) type.name.lowercase() else id}.json")
        lifespan = type.lifespanDays * 86400000L
    }

    suspend inline fun <reified DATA> save(data: DATA) {
        if (!branch.exists()) branch.mkdirs()
        FileOutputStream(file).use {
            it.write(Api.json.encodeToString(data).encodeToByteArray())
        }
    }

    suspend inline fun <reified DATA> restore(): DATA? =
        if (file.exists() && (Utils.now() - file.lastModified()) < lifespan) try {
            Api.json.decodeFromString<DATA>(
                FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }
            )
        } catch (_: Exception) {
            null
        } else null

    enum class Type(
        val isCache: Boolean,
        val lifespanDays: Int,
        val singleFile: Boolean = !isCache,
    ) {
        DOWNLOAD_LIST(false, 30),
        EXPORT_LIST(false, 30),

        SAVED(true, 1, true),
        PROFILE(true, 2),
        POSTS(true, 3),
        STORY(true, 1),
        HIGHLIGHTS(true, 7),
        TAGGED(true, 7),
    }
}
