package ir.mahdiparastesh.instatools.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Media.Version
import ir.mahdiparastesh.instatools.api.User
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.Serializable
import java.net.URI

@Entity
@Serializable
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
        /**
         * Adds this item to the download queue.
         * @param owner must be specified in stories and highlights.
         *
         * @see [ir.mahdiparastesh.instatools.data.Queued]
         * @see [ir.mahdiparastesh.instatools.job.Downloader]
         */
        suspend fun Media.queue(
            dao: Database.DAO,
            idealSize: Float = Version.BEST,
            link: String? = null,
            owner: User? = null,
            onlyOneSlide: Int? = null
        ) {
            val u = owner ?: owner()
            val now = Utils.now()
            if (carousel_media != null) for (slide in carousel_media!!.indices) {
                if (onlyOneSlide != null && onlyOneSlide != slide) continue
                val car = carousel_media!![slide]
                dao.addQueued(
                    Queued(
                        now,
                        link ?: link()!!,
                        Utils.compileSecondsTS(car.taken_at),
                        u.id(),
                        u.username!!,
                        car.id(),
                        car.nearest(idealSize)!!,
                        car.thumb(),
                        car.media_type.toInt().toByte(),
                        car.video_duration,
                        caption?.text,
                    )
                )
            } else dao.addQueued(
                Queued(
                    now,
                    link ?: link()!!,
                    Utils.compileSecondsTS(taken_at),
                    u.id(),
                    u.username!!,
                    id(),
                    nearest(idealSize)!!,
                    thumb(),
                    media_type.toInt().toByte(),
                    video_duration,
                    caption?.text,
                )
            )
        }

        fun find(it: Queued, inList: List<Queued>?): Int? {
            if (inList == null) return null
            for (i in inList.indices) if (inList[i].id == it.id) return i
            return null
        }
    }
}
