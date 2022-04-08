package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.Process.myPid
import android.os.Process.myUid
import androidx.annotation.MainThread
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Model
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.system.exitProcess

interface Persistent {
    val c: Context
    val m: Model
    val gsp: SharedPreferences
    val sp: SharedPreferences?

    fun initGsp(): SharedPreferences =
        c.getSharedPreferences("global", Context.MODE_PRIVATE)
    // While using MODE_PRIVATE, only this app can access the information within the shared preferences file.
    // GSP MUST NEVER BE DELETED!!!

    fun initSp(acc: Account?): SharedPreferences? =
        if (acc != null) c.getSharedPreferences(acc.id.toString(), Context.MODE_PRIVATE)
        else null


    fun sPreference(key: String): String? =
        sp?.getString(key, null) ?: gsp.getString(key, null)

    fun bPreference(key: String, def: Boolean): Boolean = sp?.getBoolean(key, def) ?: def
    // Unavoidably gsp was left unused for good!

    @MainThread
    fun checkFiles(@MainThread onEnd: () -> Unit = {}) {
        val path = sPreference(Settings.spStorage) ?: return
        if (!isPathAccessible(c, path)) return
        val stem = DocumentFile.fromTreeUri(c, Uri.parse(path)) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val newSet = mutableSetOf<String>()
            stem.walk().forEach { if (it.isFile && it.name != null) newSet.add(it.name!!) }
            withContext(Dispatchers.Main) {
                m.files.value = newSet
                onEnd()
            }
        }
    }


    fun needAuthentication() {
        if (Login.cameHereToAuth) return
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
        gsp.edit().remove(Login.spAccount).apply()
        m.acc = null
        if (this is BaseActivity)
            goTo(Login::class, true) { putExtra(Login.EXTRA_SHOW_AD, true) }
        m.accountSwitched()
    }

    companion object {
        fun now() = Calendar.getInstance().timeInMillis

        fun isPathAccessible(c: Context, path: String): Boolean {
            val uri = Uri.parse(path)
            return c.checkUriPermission(
                uri, myPid(), myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED && c.checkUriPermission(
                uri, myPid(), myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
