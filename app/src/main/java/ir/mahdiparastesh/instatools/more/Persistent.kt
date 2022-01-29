package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Model

interface Persistent {
    var c: Context
    var m: Model
    var esp: SharedPreferences
    var gsp: SharedPreferences
    var sp: SharedPreferences?

    companion object {
        fun initEsp(c: Context): SharedPreferences = EncryptedSharedPreferences.create(
            "main", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), c,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        fun initGsp(c: Context): SharedPreferences =
            c.getSharedPreferences("global", Context.MODE_PRIVATE)

        fun initSp(c: Context, acc: Account?): SharedPreferences? =
            if (acc != null) c.getSharedPreferences(acc.id.toString(), Context.MODE_PRIVATE)
            else null
    }
}
