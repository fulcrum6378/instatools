package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import ir.mahdiparastesh.instatools.view.UiTools

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

    fun fName(ext: String) = "${userName}_${UiTools.fileDateTime(date!!)}_$itemId.$ext"

    companion object {
        fun find(it: Queued, inList: List<Queued>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
