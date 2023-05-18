package ir.mahdiparastesh.instatools.data

import android.content.Context
import com.google.gson.Gson
import ir.mahdiparastesh.instatools.Login.Companion.spAccount
import ir.mahdiparastesh.instatools.more.Persistent
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Represents an Instagram account. */
class Account(
    var id: Long,
    var user: String? = null,
    var name: String? = null,
    var pict: String? = null,
    var cook: String? = null,
    var roll: String? = null,
    var last: Long = 0L,
    // var mfrw: Int = 0,
    // keep in mind to update the fields whose data need to persist after another Login
) {
    /*fun saveMe(c: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            save(c, load(c).apply { find(this@Account, this)?.let { this[it] = this@Account } })
        }
    }*/

    companion object {
        @Suppress("RedundantSuspendModifier")
        suspend fun load(c: Context): ArrayList<Account> {
            val secured = Secured(c)
            return if (secured.exists()) {
                var data: ByteArray? = null
                runCatching {
                    FileInputStream(secured).use { it.readBytes() }
                }.onSuccess { data = it }
                data?.let { Gson().fromJson(String(it), Array<Account>::class.java) }
                    ?.let { ArrayList(it.toList()) }
                    ?: arrayListOf()
            } else arrayListOf()
        }

        suspend fun selected(
            c: Persistent, list: List<Account>? = null, guestIfNotExists: Boolean = true
        ): Account? = (list ?: load(c.c)).find {
            it.id == c.gsp.getString(spAccount, if (guestIfNotExists) "-1" else "-2")
                ?.toLongOrNull()
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun save(c: Context, accounts: List<Account>) {
            runCatching {
                FileOutputStream(Secured(c)).use { fos ->
                    fos.write(
                        Gson().toJson(accounts.filter { it.cook != null || it.id == -1L })
                            .encodeToByteArray()
                    )
                }
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
