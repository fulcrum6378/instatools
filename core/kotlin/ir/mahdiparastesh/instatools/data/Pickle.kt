package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Caches data models in order to reduce the number of API requests.
 * Used as an object-oriented data storage as well!
 *
 * Structure:
 * 4. Leaf: a [Pickle] JSON file storing object-oriented data
 * 3. Branch: a directory where pickle files are group separated between numerous third-party
 *            profiles. It can be ignored and pickles can be stored in a Tree!
 * 2. Tree: a directory related to an InstaTools user's Account.
 * 1. Root: where all Trees are stored. There can be multiple Roots;
 *          e.g. one for main files and another for cache files.
 *
 * @param root a root directory to store pickle trees.
 */
class Pickle(root: File, acc: Long, val type: Type, id: String?) {
    val branch: File
    val leaf: File
    val jsonEngine: Json

    init {
        val tree = File(root, DIR_PREFIX + acc)
        branch = if (type.isSingleFile) tree else File(tree, type.name.lowercase())
        leaf = File(branch, "${if (type.isSingleFile) type.name.lowercase() else id}.json")
        jsonEngine = if (type.isApiCache) Api.json else Json
    }

    inline fun <reified DATA> save(data: DATA) {
        if (!branch.exists()) branch.mkdirs()
        FileOutputStream(leaf).use {
            it.write(jsonEngine.encodeToString(data).encodeToByteArray())
        }
    }

    inline fun <reified DATA> restore(): DATA? {
        if (!leaf.exists()) return null
        val lifespan = type.calculateLifespanInMillis()
        if (lifespan != 0L && (Utils.now() - leaf.lastModified()) >= lifespan) return null
        return try {
            jsonEngine.decodeFromString<DATA>(
                FileInputStream(leaf).use { it.readBytes().toString(Charsets.UTF_8) }
            )
        } catch (e: Exception) {
            throw e
            //null
        }
    }

    /**
     * @param lifespanInDays set it to zero to make it never expire.
     *                       remember that media URLs get expired eventually.
     * @param isSingleFile is the data related to the user?
     *                     or is it related to multiple third-party profiles on the internet?
     * @param isApiCache is the data created by the Instagram API?
     */
    enum class Type(
        val lifespanInDays: Float,
        val isSingleFile: Boolean = true,
        val isApiCache: Boolean = false,
    ) {

        // user data
        FAVOURITES(0f),
        DOWNLOAD_LIST(0f),
        COMMAND_LIST(7f),

        // caches
        SAVED(1f, isApiCache = true),
        PROFILE(3f, false, true),
        POSTS(3f, false, true),
        STORY(0.5f, false, true),
        HIGHLIGHTS(7f, false, true),
        TAGGED(5f, false, true);

        fun calculateLifespanInMillis(): Long = (lifespanInDays * 86400000f).toLong()
    }

    companion object {
        const val DIR_PREFIX = "pickle_"
    }
}
