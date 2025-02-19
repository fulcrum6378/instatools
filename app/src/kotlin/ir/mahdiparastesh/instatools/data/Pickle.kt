package ir.mahdiparastesh.instatools.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.MainThread
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.api.Story
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class Pickle(c: Context) {
    private val tree = File(c.cacheDir, "pickle")

    init {
        if (!tree.exists()) tree.mkdirs()
    }

    @SuppressLint("SdCardPath")
    @MainThread
    fun save(data: Any, type: Type, id: String?) {
        val data = Gson().toJson(data).encodeToByteArray()
        CoroutineScope(Dispatchers.IO).launch {
            FileOutputStream(
                if (type.single) {
                    File(tree, type.file)
                } else {
                    val branch = File(tree, type.file)
                    if (!branch.exists()) branch.mkdir()
                    File(branch, "$id.json")
                }
            ).use { it.write(data) }
        }
    }

    @Suppress("RedundantSuspendModifier")
    suspend fun <DATA> restore(type: Type, id: String?): DATA? {
        val file = if (type.single) {
            File(tree, type.file)
        } else {
            File(File(tree, type.file), "$id.json")
        }
        if (!file.exists()) return null
        // TODO expiration

        return Gson().fromJson<DATA>(
            FileInputStream(file).use { it.readBytes().toString(Charsets.UTF_8) },
            type.javaType
        )
    }

    enum class Type(
        val javaType: java.lang.reflect.Type,
        val file: String,
        val single: Boolean = false
    ) {
        SAVED(
            TypeToken.getParameterized(Rest.LazyList::class.java, Rest.SavedItem::class.java).type,
            "saved.json", true
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
