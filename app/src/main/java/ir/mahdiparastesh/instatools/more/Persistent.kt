package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Model

interface Persistent {
    val c: Context
    val m: Model
    val gsp: SharedPreferences
    val sp: SharedPreferences?

    fun initGsp(): SharedPreferences =
        c.getSharedPreferences("global", Context.MODE_PRIVATE)

    fun initSp(acc: Account?): SharedPreferences? =
        if (acc != null) c.getSharedPreferences(acc.id.toString(), Context.MODE_PRIVATE)
        else null


    fun sPreference(key: String): String? =
        sp?.getString(key, null) ?: gsp.getString(key, null)

    fun bPreference(key: String, def: Boolean): Boolean? =
        if (sp?.contains(key) == true) sp?.getBoolean(key, def)
        else gsp.getBoolean(key, def)


    fun needAuthentication() {
        val signedOut = m.acc?.id != -1L
        if (signedOut) {
            if (this is BaseActivity) goTo(Login::class, true)
        } else Toast.makeText(c, "InstaTools needs authentication!", Toast.LENGTH_SHORT).show()
        // TODO: IMPROVE THIS
    }
}
