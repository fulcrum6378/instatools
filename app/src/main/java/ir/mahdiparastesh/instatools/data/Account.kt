package ir.mahdiparastesh.instatools.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Login.Companion.spAccount
import ir.mahdiparastesh.instatools.more.Persistent
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class Account(
    var id: Long,
    var user: String? = null,
    var name: String? = null,
    var pict: String? = null,
    var cook: String? = null
) {
    companion object {
        fun load(c: Context): ArrayList<Account> {
            if (!Secured(c).exists()) return arrayListOf()
            FileInputStream(Secured(c)).let {
                return@load ArrayList(
                    Gson().fromJson(String(it.readBytes()), Array<Account>::class.java).toList()
                )
            }
        }

        fun selected(
            c: Persistent, list: List<Account> = load(c.c), guestIfNotExists: Boolean = true
        ): Account? = list.find {
            it.id == c.gsp.getString(spAccount, if (guestIfNotExists) "-1" else "-2")
                ?.toLongOrNull()
        }

        fun save(c: Context, accounts: List<Account>) {
            FileOutputStream(Secured(c)).write(
                (if (BuildConfig.DEBUG) GsonBuilder().setPrettyPrinting().create() else Gson())
                    .toJson(accounts.filter { it.cook != null || it.id == -1L }).encodeToByteArray()
            )
        }
    }

    class Secured(c: Context) : File(c.filesDir, "cache.json")
}
