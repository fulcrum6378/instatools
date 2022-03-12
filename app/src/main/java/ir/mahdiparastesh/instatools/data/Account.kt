package ir.mahdiparastesh.instatools.data

import android.content.Context
import com.google.gson.Gson
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
    var cook: String? = null,
    var roll: String? = null,
    var last: Long = 0L,
    var mfrw: Int = 0,
    // keep in mind to update the fields whose data need to persist after another Login
) {
    fun saveMe(c: Context) {
        save(c, load(c).apply { find(this@Account, this)?.let { this[it] = this@Account } })
    }

    companion object {
        fun load(c: Context): ArrayList<Account> {
            val data: ByteArray
            FileInputStream(Secured(c)).use { data = it.readBytes() }
            return if (Secured(c).exists()) ArrayList(
                Gson().fromJson(String(data), Array<Account>::class.java).toList()
            ) else arrayListOf()
        }

        fun selected(
            c: Persistent, list: List<Account> = load(c.c), guestIfNotExists: Boolean = true
        ): Account? = list.find {
            it.id == c.gsp.getString(spAccount, if (guestIfNotExists) "-1" else "-2")
                ?.toLongOrNull()
        }

        fun save(c: Context, accounts: List<Account>) {
            FileOutputStream(Secured(c)).use { fos ->
                fos.write(
                    Gson().toJson(accounts.filter { it.cook != null || it.id == -1L })
                        .encodeToByteArray()
                )
            }
        }

        fun find(it: Account, inList: List<Account>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }

    class Secured(c: Context) : File(c.filesDir, "cache.json")
    // Since this file is stored in the internal storage, using EncryptedFile is not urgent so much.
}
