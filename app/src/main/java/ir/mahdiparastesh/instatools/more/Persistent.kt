package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Model
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

    fun initSp(acc: Account?): SharedPreferences? =
        if (acc != null) c.getSharedPreferences(acc.id.toString(), Context.MODE_PRIVATE)
        else null


    fun sPreference(key: String): String? =
        sp?.getString(key, null) ?: gsp.getString(key, null)

    fun bPreference(key: String, def: Boolean): Boolean = sp?.getBoolean(key, def) ?: def
    // Unavoidably gsp was left unused for good!


    fun needAuthentication() {
        if (Login.cameHereToAuth) return
        // val signedOut = m.acc?.id != -1L
        if (this is BaseActivity)
            goTo(Login::class, true) { putExtra(Login.EXTRA_NEED_AUTH, true) }
        else {
            c.startActivity(Intent(c, Login::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Login.EXTRA_NEED_AUTH, true)
            })
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
    }

    fun switchAcc() {
        gsp.edit().remove(Login.spAccount).apply()
        m.acc = null
        if (this is BaseActivity) goTo(Login::class, true)
        m.accountSwitched()
    }

    companion object {
        fun now() = Calendar.getInstance().timeInMillis
    }
}
