package ir.mahdiparastesh.instatools.data

import android.content.Context
import com.google.gson.JsonSyntaxException
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.util.Utils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Caches data models in order to reduce the number of API requests. */
@Suppress("RedundantSuspendModifier")
class Pickle(c: Context, type: Type, id: String?) {
    private val branch: File
    val file: File

    init {
        val tree = File(c.cacheDir, "pickle")
        branch = if (type.single) tree else File(tree, type.file)
        file = File(branch, if (type.single) type.file else "$id.json")
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
        } catch (e: JsonSyntaxException) {
            if (BuildConfig.DEBUG) throw e
            else null
        } else null

    enum class Type(val file: String, val single: Boolean = false) {
        SAVED("saved.json", true),
        PROFILE("profile"),
        POSTS("posts"),
        STORY("story"),
        HIGHLIGHTS("highlights"),
        TAGGED("tagged"),
    }
}
