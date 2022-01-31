package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.SharedPreferences
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Model

interface Persistent {
    var c: Context
    var m: Model

    //var esp: SharedPreferences
    var gsp: SharedPreferences
    var sp: SharedPreferences?

    fun preference(key: String): String? =
        sp?.getString(key, null) ?: gsp.getString(key, null)

    companion object {
        // java.security.KeyStoreException:
        // the master key android-keystore://_androidx_security_master_key_ exists but is unusable
        /*fun initEsp(c: Context) = EncryptedSharedPreferences.create(
            "main", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), c,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )*/

        fun initGsp(c: Context): SharedPreferences =
            c.getSharedPreferences("global", Context.MODE_PRIVATE)

        fun initSp(c: Context, acc: Account?): SharedPreferences? =
            if (acc != null) c.getSharedPreferences(acc.id.toString(), Context.MODE_PRIVATE)
            else null
    }
}
