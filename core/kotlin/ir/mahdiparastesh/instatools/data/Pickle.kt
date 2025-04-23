package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.User
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.SerializationException
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
 * 3. Branch: a directory where pickle files are group separated between numerous third-person
 *            profiles. It can be ignored and pickles can be stored in a Tree!
 * 2. Tree: a directory related to an InstaTools user's Account.
 * 1. Root: where all Trees are stored. There can be multiple Roots;
 *          e.g. one for main files and another for cache files.
 *
 * @param root a root directory to store pickle trees.
 * @param acc [User.id] of the first-person user (owner of the device)
 * @param type data type of the Pickle
 * @param id [User.id] of the third-person user whose content is being viewed and saved (pickled)
 */
class Pickle(root: File, acc: Long, val type: Type, id: String?) {

    val branch: File = branch(root, type, acc)
    val leaf: File = File(branch, "${if (type.isSingleFile) type.name.lowercase() else id}.json")
    val jsonEngine: Json = if (type.isApiCache) Api.json else Json

    inline fun <reified DATA> save(data: DATA) {
        if (!branch.exists()) branch.mkdirs()
        FileOutputStream(leaf).use {
            it.write(jsonEngine.encodeToString(data).encodeToByteArray())
        }
    }

    /**
     * @return the Pickle data if the specified file is available and not expired, null otherwise
     */
    inline fun <reified DATA> restore(): DATA? {

        // return null if the Pickle doesn't exist
        if (!leaf.exists()) return null

        // avoid using the pickle if it is expired
        if (type.lifespan >= 0f && (Utils.now() - leaf.lastModified()) >= type.lifespan) return null

        // load the Pickle
        return try {
            jsonEngine.decodeFromString<DATA>(
                FileInputStream(leaf).use { it.readBytes().toString(Charsets.UTF_8) }
            )
        } catch (e: Exception) {
            if (e !is SerializationException) throw e
            null
        }
    }

    /**
     * @param lifespan after N milliseconds the pickle is considered as expired.
     *                 set it to a negative number to make it never expire.
     *                 remember that IG Media URLs get expired eventually.
     *                 all API cache files MUST have an expiration time.
     * @param isSingleFile is the data related to the user?
     *                     or is it related to multiple third-party profiles on the internet?
     * @param isApiCache is the data created by the Instagram API?
     */
    enum class Type(
        val lifespan: Float,
        val isSingleFile: Boolean = true,
        val isApiCache: Boolean = false,
    ) {

        // user data
        FAVOURITES(-1f * DAY),
        DOWNLOAD_LIST(-1f * DAY),
        COMMAND_LIST(7f * DAY),

        // API cache
        SAVED(1f * DAY, isApiCache = true),
        PROFILE(3f * DAY, false, true),  // 5 days didn't suffice
        POSTS(2f * DAY, false, true),
        STORY(0.5f * DAY, false, true),
        HIGHLIGHTS(7f * DAY, false, true),
        REELS(2f * DAY, false, true),
        TAGGED(5f * DAY, false, true)
    }

    companion object {
        const val DIR_PREFIX = "pickle_"
        const val DAY = 86400000f

        fun branch(root: File, type: Type, acc: Long): File {
            val tree = File(root, DIR_PREFIX + acc)
            return if (type.isSingleFile) tree else File(tree, type.name.lowercase())
        }
    }
}
