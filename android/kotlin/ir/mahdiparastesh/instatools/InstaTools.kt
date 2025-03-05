package ir.mahdiparastesh.instatools

import android.app.Application
import android.content.SharedPreferences
import android.util.DisplayMetrics
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.Settings.Companion.cacheSize
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.util.Queue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

class InstaTools : Application() {
    /** Current [Account] for using the Instagram API */
    var acc: Account? = null

    /** Global Shared Preferences */
    val gsp: SharedPreferences by lazy { getSharedPreferences(Settings.GSP, MODE_PRIVATE) }

    /** Local Shared Preferences */
    var sp: SharedPreferences? = null

    /** One queue to rule all [Download]s */
    lateinit var downloads: Queue<Download>

    /** One queue to rule all [Command]s */
    lateinit var commands: Queue<Command>

    /** A long list of locally stored media */
    var downloadHistory: CopyOnWriteArraySet<String>? = null

    /** List of all [Favourite]s */
    var fav: MutableLiveData<HashSet<Favourite>?> = MutableLiveData(null)
    private var favPickle: Pickle? = null

    /** Screen dimensions */
    val dm: DisplayMetrics by lazy { resources.displayMetrics }


    override fun onCreate() {
        super.onCreate()
        if (acc == null) acc = Account.selected(this)
            ?.also { onLoggedIn(it, false) }
    }

    fun onLoggedIn(acc: Account, changed: Boolean) {
        this.acc = acc
        Api.cookies = acc.cook ?: ""
        val sid = acc.id.toString()
        if (changed) gsp.edit { putString(Login.SP_ACCOUNT, sid) }
        CoroutineScope(Dispatchers.IO).launch {
            sp = getSharedPreferences(sid, MODE_PRIVATE)
            downloads = Queue(Pickle(filesDir, acc.id, Pickle.Type.DOWNLOAD_LIST, null))
            commands = Queue(Pickle(filesDir, acc.id, Pickle.Type.COMMAND_LIST, null))
            favPickle = Pickle(filesDir, acc.id, Pickle.Type.FAVOURITES, null)
            val favourites = favPickle?.restore<List<Favourite>>()?.toHashSet()
            withContext(Dispatchers.Main) { fav.value = favourites }
        }
    }

    fun onLoggedOut() {
        fav.value = null
        favPickle = null
        //commands = null
        //downloads = null
        sp = null
        gsp.edit { remove(Login.SP_ACCOUNT) }
        Api.cookies = ""
        acc = null
    }

    /**
     * Gets a local NON-BOOLEAN preference using a key, if not available uses the global preference.
     */
    fun sPreference(key: String): String? =
        sp?.getString(key, null) ?: gsp.getString(key, null)

    /**
     * Gets a local BOOLEAN preference using a key, if not available uses the global preference.
     */
    fun bPreference(key: String, def: Boolean, keyCb: String, keyCBDef: Boolean): Boolean =
        if (sp?.getBoolean(keyCb, keyCBDef) == true && sp?.contains(key) == true)
            sp!!.getBoolean(key, def)
        else gsp.getBoolean(key, def)

    fun incrementCounter(key: String) {
        gsp.edit { putLong(key, gsp.getLong(key, 0L) + 1L) }
    }

    @MainThread
    fun addFavourite(item: Favourite) {
        if (fav.value == null) fav.value = hashSetOf<Favourite>()
        fav.value?.also { favourites ->
            favourites.remove(item)
            favourites.add(item)
            CoroutineScope(Dispatchers.IO).launch {
                favPickle?.save(favourites)
            }
        }
    }

    @MainThread
    fun removeFavourite(item: Favourite) {
        fav.value?.also { favourites ->
            favourites.remove(item)
            CoroutineScope(Dispatchers.IO).launch {
                favPickle?.save(favourites)
            }
        }
    }

    fun clearCacheIfNecessary(subdir: String? = null): Boolean {
        if (cacheSize() <= gsp.getLong(Settings.spCacheLimit, Settings.defaultCacheLimit(this)))
            return false
        (if (subdir != null) File(cacheDir, subdir) else cacheDir)
            .deleteRecursively()
        return true
    }

    fun deletePickles(accountId: String = acc!!.id.toString()) {
        File(filesDir, Pickle.DIR_PREFIX + accountId).deleteRecursively()
        File(cacheDir, Pickle.DIR_PREFIX + accountId).deleteRecursively()
    }

    fun deleteSp(accountId: String = acc!!.id.toString()) {
        File(getDir("shared_prefs", MODE_PRIVATE), "$accountId.xml")
            .apply { if (exists()) delete() }
    }
}

/* TODO:
  * Problems:
  * Some of ListPost's selected items do not appear to be selected and
  *     the same items might appear as selected at another selection!!
  *     Related to: Selection indicator invisible after Expandable collapses
  * Search doesn't work at all
  * on configuration changes (especially Login while browsing the web)
  * Invalid Pickle data detected in posts!
  * -
  * Extension:
  * A simple following list Activity
  * A new proper logo
  * Choose download qualities in the Toolbar of Downloads
  * Custom icons for the Services
  * Percentage of downloads
  * Make CommandService cancellable and pausable
  * Switch to ComponentActivity
  *
  * NOTES:
  * - Inconsistency is detected in a RecyclerView whenever you don't notify it completely of the changes!
*/
