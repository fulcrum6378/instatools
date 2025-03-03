package ir.mahdiparastesh.instatools

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.Process.myPid
import android.os.Process.myUid
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.Settings.Companion.cacheSize
import ir.mahdiparastesh.instatools.Settings.Companion.defaultCacheLimit
import ir.mahdiparastesh.instatools.Settings.Companion.spCacheLimit
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Account.Companion.dbName
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Queue
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.system.exitProcess

class InstaTools : Application() {
    /** Current [Account] for using the Instagram API */
    var acc: Account? = null

    val dbLazy: Lazy<Database> = lazy { Database.build(this, acc.dbName()) }
    val db: Database by dbLazy
    val dao: Database.DAO by lazy { db.dao() }

    /** Global Shared Preferences */
    val gsp: SharedPreferences by lazy { getSharedPreferences(GSP, MODE_PRIVATE) }

    /** Local Shared Preferences */
    val sp: SharedPreferences? by lazy {
        if (acc != null) getSharedPreferences(acc!!.id.toString(), MODE_PRIVATE) else null
    }

    /** One queue to rule all [Download]s */
    val downloads: Queue<Download> by lazy {
        Queue(Pickle(filesDir, acc!!.id, Pickle.Type.DOWNLOAD_LIST, null))
    }

    /** One queue to rule all [Command]s */
    val commands: Queue<Command> by lazy {
        Queue(Pickle(filesDir, acc!!.id, Pickle.Type.COMMAND_LIST, null))
    }

    /** A long list of locally stored media */
    var downloadHistory: CopyOnWriteArraySet<String>? = null

    /** List of all [Favourite]s */
    var fav: ArrayList<Favourite>? = null


    override fun onCreate() {
        super.onCreate()
        if (acc == null) acc = Account.selected(this)
            ?.also { selectAccount(it) }
    }

    fun selectAccount(acc: Account) {
        this.acc = acc
        Api.cookies = acc.cook ?: ""
    }

    /** Switches between [Account] instances. */
    fun switchAcc() {
        gsp.edit { remove(Login.SP_ACCOUNT) }
        acc = null
        if (this is BaseActivity) // FIXME
            goTo(Login::class, true)
        accountSwitched()
    }

    /** Call this when you change [InstaTools.acc] */
    fun accountSwitched() {
        fav = null
        db.close()
    }

    /**
     * If Instagram detects the app as a robot, this method must be invoked for the proper actions
     * to be taken right away!
     */
    fun needAuthentication() {
        if (Login.browsePurpose == Login.BROWSE_AUTH_REQ) return
        ForegroundService.terminateTasks(this)
        gsp.edit { remove(Login.SP_ACCOUNT) }
        accountSwitched()
        if (this is BaseActivity && this !is Login)
            goTo(Login::class, true) { putExtra(Login.EXTRA_NEED_AUTH, acc?.id ?: -1L) }
        else {
            startActivity(Intent(this, Login::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Login.EXTRA_NEED_AUTH, true)
            })
            Process.killProcess(myPid())
            exitProcess(0)
        }
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
        if (dbLazy.isInitialized() && !anyoneAlive()) db.close()
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
