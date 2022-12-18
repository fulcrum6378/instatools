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
    var status: Byte = 0, // 0=>Pending, 1=>Failed, 2=>Suspended
    var dur: Long? = null, // in seconds
    var caption: String? = null,
) {
    @PrimaryKey(autoGenerate = true)
    var id = 0L

    fun fName(ext: String) = "${userName}_${UiTools.fileDateTime(date!!)}_$itemId.$ext"

    fun isMainFile() = mediaType !in arrayOf(3.toByte())

    //fun isReady() = status == 0.toByte()
    fun isFailed() = status == 1.toByte()

    companion object {
        fun find(it: Queued, inList: List<Queued>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
