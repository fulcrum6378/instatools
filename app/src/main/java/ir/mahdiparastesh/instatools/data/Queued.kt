package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    @PrimaryKey(autoGenerate = true) var id = 0L

    companion object {
        fun find(it: Queued, inList: List<Queued>): Int? {
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
