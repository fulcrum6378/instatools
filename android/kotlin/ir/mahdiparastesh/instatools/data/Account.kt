package ir.mahdiparastesh.instatools.data

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.Login.Companion.SP_ACCOUNT
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Represents an Instagram account used in the Login activity. */
@Serializable
class Account(
    var id: Long,
    var user: String? = null,
    var name: String? = null,
    var pict: String? = null,
    var cook: String? = null,
    var last_auth: Long = Utils.now(),
    var last_used: Long = Utils.now(),
    // keep in mind to update the fields whose data need to persist after another Login
) {

    @MainThread
    fun saveMeInIO(c: Context) {
        CoroutineScope(Dispatchers.IO).launch { saveMe(c) }
    }

    @WorkerThread
    fun saveMe(c: Context) {
        save(
            c, load(c)
                .apply { find(this@Account, this)?.let { this[it] = this@Account } })
    }

    fun justAuthenticated() {
        val now = Utils.now()
        last_auth = now
        last_used = now
    }

    fun resetAuthDate() {
        last_auth = 0
    }

    companion object {
        fun load(c: Context): ArrayList<Account> {
            val secured = Secured(c)
            return if (secured.exists()) {
                var data: ByteArray? = null
                runCatching {
                    FileInputStream(secured).use { it.readBytes() }
                }.onSuccess { data = it }
                data?.let {
                    ArrayList(
                        Api.json.decodeFromString<Array<Account>>(String(it)).toList()
                    )
                }
                    ?: arrayListOf()
            } else arrayListOf()
        }

        fun selected(c: InstaTools): Account? {
            val id = c.gsp.getString(SP_ACCOUNT, null)?.toLongOrNull() ?: return null
            return load(c).find { it.id == id }
        }

        fun save(c: Context, accounts: List<Account>) {
            FileOutputStream(Secured(c)).use { fos ->
                fos.write(Api.json.encodeToString(accounts).encodeToByteArray())
            }
        }

        fun find(it: Account, inList: List<Account>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }

    class Secured(c: Context) : File(c.filesDir, "cache.json")
}
