package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import ir.mahdiparastesh.instatools.util.Utils
import java.net.URI

@Entity
class Queued(
    val addedAt: Long,
    val link: String, // can be empty
    var date: Long,
    var userId: String,
    var userName: String,
    var mediaId: String,
    var url: String,
    var thumb: String?,
    var type: Byte,
    var dur: Float?, // in seconds
    var caption: String?,
    var status: Byte = 0 // 0=>Pending, 1=>Failed, 2=>Suspended
) {
    @PrimaryKey(autoGenerate = true)
    var id = 0L

    fun fName(ext: String) = "${userName}_${Utils.fileDateTime(date)}_$mediaId.$ext"

    fun extension() = URI(url).path.split(".").last()

    fun isMainFile() = type.toInt() !in arrayOf(3)

    fun isFailed() = status == 1.toByte()

    companion object {
        fun find(it: Queued, inList: List<Queued>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
