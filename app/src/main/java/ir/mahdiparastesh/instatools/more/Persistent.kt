package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.SharedPreferences
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Model

interface Persistent {
    val c: Context
    val m: Model
    val gsp: SharedPreferences
    val sp: SharedPreferences?

    fun sPreference(key: String): String? =
        sp?.getString(key, null) ?: gsp.getString(key, null)

    fun bPreference(key: String, def: Boolean): Boolean? =
        if (sp?.contains(key) == true) sp?.getBoolean(key, def)
        else gsp.getBoolean(key, def)

    companion object {
        fun initGsp(c: Context): SharedPreferences =
            c.getSharedPreferences("global", Context.MODE_PRIVATE)

        fun initSp(c: Context, acc: Account?): SharedPreferences? =
            if (acc != null) c.getSharedPreferences(acc.id.toString(), Context.MODE_PRIVATE)
            else null
    }
}
