package ir.mahdiparastesh.instatools.more

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
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import kotlin.system.exitProcess

/**
 * An important interface which holds everything for storing data (persistence),
 * including databases, shared preferences and view models.
 */
interface Persistent {
    val c: Context
    val m: Model
    val gsp: SharedPreferences
    val sp: SharedPreferences?
    val dbLazy: Lazy<Database>
    val db: Database
    val dao: Database.DAO

    /** Initialises the global shared preferences. GSP MUST NEVER BE DELETED!!! */
    fun initGsp(): SharedPreferences =
        c.getSharedPreferences(GSP, Context.MODE_PRIVATE)
    // While using MODE_PRIVATE, only this app can access the information within the shared preferences file.

    /** Initialises the local shared preferences. */
    fun initSp(acc: Account?): SharedPreferences? =
        if (acc != null) c.getSharedPreferences(acc.id.toString(), Context.MODE_PRIVATE)
        else null


    /**
     * Gets a local NON-BOOLEAN preference using a key, if not available uses the global preference.
     */
    fun sPreference(key: String): String? =
        sp?.getString(key, null) ?: gsp.getString(key, null)

    /**
     * Gets a local BOOLEAN preference using a key, if not available uses the global preference.
     */
    fun bPreference(key: String, keyCb: String, def: Boolean): Boolean =
        if (sp?.getBoolean(keyCb, false) == true && sp?.contains(key) == true)
            sp!!.getBoolean(key, true/*IMPOSSIBLE*/)
        else gsp.getBoolean(key, def)


    /**
     * If Instagram detects the app as a robot, this method must be invoked for the proper actions
     * to be taken right away!
     */
    fun needAuthentication() {
        if (Login.browsePurpose == Login.BROWSE_AUTH_REQ) return
        ForegroundService.terminateTasks(c)
        gsp.edit { remove(Login.spAccount) }
        m.accountSwitched()
        if (this is BaseActivity && this !is Login)
            goTo(Login::class, true) { putExtra(Login.EXTRA_NEED_AUTH, m.acc?.id ?: -1L) }
        else {
            c.startActivity(Intent(c, Login::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Login.EXTRA_NEED_AUTH, true)
            })
            Process.killProcess(myPid())
            exitProcess(0)
        }
    }

    /** Switches between Account instances. */
    fun switchAcc() {
        gsp.edit { remove(Login.spAccount) }
        m.acc = null
        if (this is BaseActivity)
            goTo(Login::class, true)
        m.accountSwitched()
    }

    companion object {
        const val GSP = "global"

        fun now() = System.currentTimeMillis()

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
