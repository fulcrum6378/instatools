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
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.util.ForegroundService
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

    fun clearCacheIfNecessary(subdir: String? = null): Boolean {
        if (cacheSize() <= gsp.getLong(Settings.spCacheLimit, defaultCacheLimit()))
            return false
        (if (subdir != null) File(cacheDir, subdir) else cacheDir)
            .deleteRecursively()
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

    /**
     * If Instagram detects the app as a robot, this method must be invoked for the proper actions
     * to be taken right away!
     */
    fun needAuthentication() {
        ForegroundService.terminateTasks(this)
        gsp.edit { remove(Login.SP_ACCOUNT) }
        onLoggedOut()
        startActivity(Intent(this, Login::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Login.EXTRA_NEED_AUTH, true)
        })
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
  * Selection indicator invisible after Expandable collapses in all PostSelectors
  * PageVwr replaces lazily loaded posts with entire previously loaded posts sometimes
  * -
  * Extension:
  * PageTry
  * Choose download qualities in the Toolbar of Downloads
  * Custom icons for the Services
  * Make CommandService cancellable and pausable
  * Switch to ComponentActivity
  * -
  * Take the audio Download and the profile Download to :core
  *
  * NOTES:
  * Inconsistency is detected in a RecyclerView whenever you don't notify it completely of the changes!
*/
