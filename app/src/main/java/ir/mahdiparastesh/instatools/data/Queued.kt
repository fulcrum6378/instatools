package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import ir.mahdiparastesh.instatools.view.UiTools.Companion.z
import java.util.*

@Entity
class Queued(
    val addedAt: Long,
    val link: String,
    var date: Long? = null,
    var userId: String? = null,
    var userName: String? = null,
    var itemId: String? = null,
    var url: String? = null,
    var thumb: String? = null,
    var mediaType: Byte? = null,
    var failed: Boolean = false
) {
    @PrimaryKey(autoGenerate = true)
    var id = 0L

    fun fName(ext: String, includeUn: Boolean = false): String {
        val cal = Calendar.getInstance().apply { timeInMillis = date!! }
        return (if (includeUn) "${userName}_" else "") +
                "${cal[Calendar.YEAR]}${z(cal[Calendar.MONTH] + 1)}" +
                "${z(cal[Calendar.DAY_OF_MONTH])}_${z(cal[Calendar.HOUR_OF_DAY])}" +
                "${z(cal[Calendar.MINUTE])}${z(cal[Calendar.SECOND])}_$itemId.$ext"
    }

    companion object {
        fun find(it: Queued, inList: List<Queued>): Int? {
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
