package ir.mahdiparastesh.instatools.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.api.User
import ir.mahdiparastesh.instatools.util.Utils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Caches data models in order to reduce the number of API requests. */
@Suppress("RedundantSuspendModifier")
class Pickle(c: Context, private val type: Type, id: String?) {
    private val branch: File
    private val file: File

    init {
        val tree = File(c.cacheDir, "pickle")
        branch = if (type.single) tree else File(tree, type.file)
        file = File(branch, if (type.single) type.file else "$id.json")
    }

    suspend fun save(data: Any) {
        if (!branch.exists()) branch.mkdirs()
        FileOutputStream(file).use {
            it.write(Gson().toJson(data).encodeToByteArray())
        }
    }

    suspend fun <DATA> restore(): DATA? =
        if (file.exists() && (Utils.now() - file.lastModified()) < 2 * 86400000L) try {
            Gson().fromJson<DATA>(
                FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) },
                type.javaType
            )
        } catch (e: JsonSyntaxException) {
            if (BuildConfig.DEBUG) throw e
            else null
        } else null

    enum class Type(
        val javaType: java.lang.reflect.Type,
        val file: String,
        val single: Boolean = false
    ) {
        SAVED(
            TypeToken.getParameterized(Rest.LazyList::class.java, Rest.SavedItem::class.java).type,
            "saved.json", true
        ),
        PROFILE(
            Array<User>::class.java, "profile"
        ),
        POSTS(
            TypeToken.getParameterized(Page::class.java, Media::class.java).type, "posts"
        ),
        STORY(
            Story::class.java, "story"
        ),
        HIGHLIGHTS(
            TypeToken.getParameterized(Page::class.java, Story::class.java).type, "highlights"
        ),
        TAGGED(
            TypeToken.getParameterized(Page::class.java, Media::class.java).type, "tagged"
        ),
    }
}
