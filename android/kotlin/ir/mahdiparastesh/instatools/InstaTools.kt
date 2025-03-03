package ir.mahdiparastesh.instatools

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process.myPid
import android.os.Process.myUid
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.Settings.Companion.cacheSize
import ir.mahdiparastesh.instatools.Settings.Companion.defaultCacheLimit
import ir.mahdiparastesh.instatools.Settings.Companion.spCacheLimit
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Queue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

class InstaTools : Application() {
    /** Current [Account] for using the Instagram API */
    var acc: Account? = null

    /** Global Shared Preferences */
    val gsp: SharedPreferences by lazy { getSharedPreferences(GSP, MODE_PRIVATE) }

    /** Local Shared Preferences */
    var sp: SharedPreferences? = null

    /** One queue to rule all [Download]s */
    lateinit var downloads: Queue<Download>

    /** One queue to rule all [Command]s */
    lateinit var commands: Queue<Command>

    /** A long list of locally stored media */
    var downloadHistory: CopyOnWriteArraySet<String>? = null

    /** List of all [Favourite]s */
    var fav: ArrayList<Favourite>? = null
    private var db: Database? = null
    lateinit var dao: Database.DAO


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
            db = Database.build(this@InstaTools, sid)
            dao = db!!.dao()
        }
    }

    fun onLoggedOut() {
        fav = null
        db?.close()
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

    fun clearCacheIfNecessary(subdir: String? = null): Boolean {
        if (cacheSize() <= gsp.getLong(spCacheLimit, defaultCacheLimit(this))) return false
        (if (subdir != null) File(cacheDir, subdir) else cacheDir)
            .deleteRecursively()
        return true
    }

    fun onChildDestroyed() {
        if (!anyoneAlive()) db?.close()
    }


    companion object {
        const val GSP = "global"

        /** Are any Activities or Services alive? */
        fun anyoneAlive() = BaseActivity.anyActive() || ForegroundService.anyRunning()

        fun Context.isPathAccessible(uri: Uri): Boolean =
            checkUriPermission(
                uri, myPid(), myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED && checkUriPermission(
                uri, myPid(), myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED &&
                DocumentFile.fromTreeUri(this, uri)?.exists() == true

        fun Context.isPathAccessible(path: String) = isPathAccessible(Uri.parse(path))
    }
}

/* TODO:
  * Problems:
  * on configuration changes (especially Login while browsing the web)
  * PageVwr doesn't show its jumper
  * -
  * Extension:
  * A new proper logo
  * number of posts on PageVwr
  * Choose download qualities in the Toolbar of Downloads
  * Custom icons for the Services
  * Percentage of downloads
  * Undo for Unsave
  * Make CommandService cancellable and pausable
  *
  * NOTES:
  * - Inconsistency is detected in a RecyclerView whenever you don't notify it completely of the changes!
*/
