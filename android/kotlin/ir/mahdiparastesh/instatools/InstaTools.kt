package ir.mahdiparastesh.instatools

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.StatFs
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.Settings.Companion.toBytes
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.base.ForegroundService
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.data.DownloadHistory
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.util.Queue
import ir.mahdiparastesh.instatools.util.Utils
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
        DownloadHistory(File(cacheDir, "download_history.txt"))
    }

    /** List of all [Favourite]s */
    var fav: MutableLiveData<HashSet<Favourite>?> = MutableLiveData(null)
    private var favPickle: Pickle? = null


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

    @MainThread
    fun removeFavourite(item: Favourite) {
        fav.value?.also { favourites ->
            favourites.remove(item)
            CoroutineScope(Dispatchers.IO).launch {
                favPickle?.save(favourites)
            }
        }
    }

    fun clearCacheIfNecessary(subdir: String): Boolean {
        if (cacheSize() <= gsp.getLong(Settings.spCacheLimit, defaultCacheLimit()))
            return false
        File(cacheDir, subdir).deleteRecursively()
        return true
    }

    fun cacheSize() = cacheDir.walk().sumOf { it.length() } - 4096L

    fun defaultCacheLimit(): Long = getExternalFilesDir(null)?.let {
        val minie = resources.getInteger(R.integer.stCacheMin)
        val maxie = resources.getInteger(R.integer.stCacheMaxNominal) + minie
        val stat = StatFs(it.path)
        var ret = (stat.blockSizeLong * stat.availableBlocksLong) / 275L
        if (ret < minie.toBytes()) ret = minie.toBytes()
        if (ret > maxie.toBytes()) ret = maxie.toBytes()
        return ret
    } ?: Settings.defSpCacheLimit

    fun deleteExpiredCachePickles(acc: Long) {
        val now = Utils.now()
        for (pickleType in Pickle.Type.entries) {
            if (!pickleType.isApiCache) continue
            val branch = Pickle.branch(cacheDir, pickleType, acc)
            if (!branch.exists()) continue

            for (leaf in branch.listFiles()!!)
                if ((now - leaf.lastModified()) >= pickleType.lifespan)
                    leaf.delete()
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
  * UX Problems:
  * Services get messed up when needAuthentication() is invoked
  * REELS AND TAGGED POSTS ARE NOT DOWNLOADABLE!!!
  * Tagged(/reel?) videos cannot be watched
  * -
  * UI Problems:
  * Font for PopupMenus
  * SeekBar should be themed
  * SwipeRefreshLayout's spinner should be themed
  * -
  * Extension:
  * A button for indexing a folder
  * Choose download qualities in the Toolbar of Downloads
  * Custom icons for the Services
  * Make CommandService cancellable and pausable
  * https://stackoverflow.com/questions/34891352/android-choose-file-button-in-webview
  * -
  * Too Complicated:
  * When clicked on error notifications, no activity is opened
  * Clicking on external links will bring a "cleartext not permitted" error
  * Metadata for HEIC and MP4?
  *
  * Notes:
  * The GraphQL queries cannot like or unlike old IGTVs.
*/
