package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.util.Utils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Caches data models in order to reduce the number of API requests. */
@Suppress("RedundantSuspendModifier")
class Pickle(root: File, acc: Long, type: Type, id: String?) {
    private val branch: File
    val file: File

    init {
        val tree = File(root, "pickle_$acc")
        branch = if (type.single) tree else File(tree, type.name.lowercase())
        file = File(branch, "${if (type.single) type.name.lowercase() else id}.json")
    }

    suspend fun save(data: Any) {
        if (!branch.exists()) branch.mkdirs()
        FileOutputStream(file).use {
            it.write(Api.json.encodeToString(data).encodeToByteArray())
        }
    }

    suspend inline fun <reified DATA> restore(): DATA? =
        if (file.exists() && (Utils.now() - file.lastModified()) < 2 * 86400000L) try {
            Api.json.decodeFromString<DATA>(
                FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) }
            )
        } catch (_: Exception) {
            null
        } else null

    enum class Type(
        val isCache: Boolean,
        val single: Boolean = !isCache,
    ) {
        DOWNLOAD_LIST(false),
        EXPORT_LIST(false),

        SAVED(true, true),
        PROFILE(true),
        POSTS(true),
        STORY(true),
        HIGHLIGHTS(true),
        TAGGED(true),
    }
}
