package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.Login.Companion.SP_ACCOUNT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Represents an Instagram account. */
@Serializable
class Account(
    var id: Long,
    var user: String? = null,
    var name: String? = null,
    var pict: String? = null,
    var cook: String? = null,
    var last: Long = 0L,
    // var mfrw: Int = 0,
    // keep in mind to update the fields whose data need to persist after another Login
) {
    @MainThread
    fun saveMeInIO(c: Context) {
        CoroutineScope(Dispatchers.IO).launch { saveMe(c) }
    }

    @WorkerThread
    suspend fun saveMe(c: Context) {
        save(c, load(c).apply { find(this@Account, this)?.let { this[it] = this@Account } })
    }

    companion object {
        fun load(c: Context): ArrayList<Account> {
            val secured = Secured(c)
            return if (secured.exists()) {
                var data: ByteArray? = null
                runCatching {
                    FileInputStream(secured).use { it.readBytes() }
                }.onSuccess { data = it }
                data?.let { ArrayList(Json.decodeFromString<Array<Account>>(String(it)).toList()) }
                    ?: arrayListOf()
            } else arrayListOf()
        }

        fun selected(c: InstaTools): Account? {
            val id = c.gsp.getString(SP_ACCOUNT, null)?.toLongOrNull() ?: return null
            return load(c).find { it.id == id }
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun save(c: Context, accounts: List<Account>) {
            runCatching {
                FileOutputStream(Secured(c)).use { fos ->
                    fos.write(Json.encodeToString(accounts).encodeToByteArray())
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
