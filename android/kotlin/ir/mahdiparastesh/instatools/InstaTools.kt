package ir.mahdiparastesh.instatools

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.base.ForegroundService
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.data.DownloadHistory
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.util.Queue
import ir.mahdiparastesh.instatools.util.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val downloadHistory: DownloadHistory by lazy {
        DownloadHistory(File(filesDir, "download_history.txt"))
    }

    /** List of all [Favourite]s */
    var fav: MutableLiveData<HashSet<Favourite>?> = MutableLiveData(null)
    private var favPickle: Pickle? = null

    /** Manages files inside the private internal storage. */
    val storageManager: StorageManager by lazy { StorageManager(this) }


    override fun onCreate() {
        super.onCreate()
        if (acc == null) Account.selected(this)
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

    @MainThread
    fun onLoggedOut() {
        fav.value = null  // must be run on the main thread
        favPickle = null
        commands.forget<Command>()
        downloads.forget<Download>()
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

    fun incrementCounter(key: String, howMany: Long = 1L) {
        gsp.edit { putLong(key, gsp.getLong(key, 0L) + howMany) }
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

    fun onFavouritesUpdated() {
        fav.value?.also { favourites ->
            favPickle?.save(favourites)
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

    /**
     * If Instagram detects the app as a robot, this method must be invoked for the proper actions
     * to be taken right away!
     */
    fun needAuthentication() {
        ForegroundService.terminateTasks(this)
        gsp.edit { remove(Login.SP_ACCOUNT) }
        CoroutineScope(Dispatchers.Main).launch {
            onLoggedOut()
            startActivity(Intent(this@InstaTools, Login::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Login.EXTRA_NEED_AUTH, true)
            })
        }
    }
}

/* TODO:
  * Problems:
  * Download Media of Reels and Tagged posts before passing them to Expandable
  * Progress dialogs for Downloader, ListRel, ListTag and PageVwr.Header
  * PopupMenus by EasyPopupMenu have wrong corner strokes
  * -
  * Extensions:
  * Migrate to java.time
  * Reloading for each ListTry item
  * Choosing download qualities through long clicks on download buttons
  * Custom icons for the Services
  * Make CommandService cancellable and pausable
  * Trace the last raster icon: unsave_download
  * https://stackoverflow.com/questions/34891352/android-choose-file-button-in-webview
  * -
  * Too Complicated:
  * When clicked on error notifications, no activity is opened, but it's not so on progress ntfs!!
  * Clicking on external links will bring a "cleartext not permitted" error
  * Metadata for HEIC and MP4?
  *
  * Notes:
  * - The GraphQL queries cannot like or unlike old IGTVs.
  * - Either Android 11 or Samsung devices don't detect custom fonts for PopupMenus.
  * - Even the official Instagram app sometimes receives duplicate lazy-list content.
*/
