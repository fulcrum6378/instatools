package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import ir.mahdiparastesh.instatools.more.UiTools.Companion.z
import java.util.*

@Entity
class Queued(
    var userId: String,
    var userName: String,
    var itemId: String,
    var url: String?,
    var thumb: String?,
    var mediaType: Byte,
    var added: Long,
    var fromSaved: Boolean = false,
    var failed: Boolean = false
) {
    @PrimaryKey(autoGenerate = true)
    var id = 0L

    fun fName(ext: String): String {
        val cal = Calendar.getInstance().apply { timeInMillis = added }
        return "${cal[Calendar.YEAR]}${z(cal[Calendar.MONTH] + 1)}" +
                "${z(cal[Calendar.DAY_OF_MONTH])}_${z(cal[Calendar.HOUR])}" +
                "${z(cal[Calendar.MINUTE])}${z(cal[Calendar.SECOND])}_$itemId.$ext"
    }

    companion object {
        fun find(it: Queued, inList: List<Queued>): Int? {
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
