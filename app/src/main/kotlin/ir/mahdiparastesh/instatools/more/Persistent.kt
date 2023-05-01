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
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import java.util.*
import kotlin.system.exitProcess

interface Persistent {
    val c: Context
    val m: Model
    val gsp: SharedPreferences
    val sp: SharedPreferences?
    val dbLazy: Lazy<Database>
    val db: Database
    val dao: Database.DAO

    fun initGsp(): SharedPreferences =
        c.getSharedPreferences("global", Context.MODE_PRIVATE)
    // While using MODE_PRIVATE, only this app can access the information within the shared preferences file.
    // GSP MUST NEVER BE DELETED!!!

    fun initSp(acc: Account?): SharedPreferences? =
        if (acc != null) c.getSharedPreferences(acc.id.toString(), Context.MODE_PRIVATE)
        else null


    fun sPreference(key: String): String? =
        sp?.getString(key, null) ?: gsp.getString(key, null)

    fun bPreference(key: String, keyCb: String, def: Boolean): Boolean =
        if (sp?.getBoolean(keyCb, false) == true && sp?.contains(key) == true)
            sp!!.getBoolean(key, true/*IMPOSSIBLE*/)
        else gsp.getBoolean(key, def)


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

    fun switchAcc() {
        gsp.edit { remove(Login.spAccount) }
        m.acc = null
        if (this is BaseActivity)
            goTo(Login::class, true)
        m.accountSwitched()
    }

    companion object {
        fun now() = Calendar.getInstance().timeInMillis

        fun Context.isPathAccessible(uri: Uri): Boolean =
            checkUriPermission(
                uri, myPid(), myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED && checkUriPermission(
                uri, myPid(), myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED

        fun Context.isPathAccessible(path: String) = isPathAccessible(Uri.parse(path))
    }
}
