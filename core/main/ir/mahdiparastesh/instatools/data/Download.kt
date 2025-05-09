package ir.mahdiparastesh.instatools.data

import com.ashampoo.kim.model.GpsCoordinates
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * @param id unique ID of a [Media]
 * @param date date posted on Instagram
 * @param url of the desired version
 * @param type [Media.Type.num]
 * @param owner user name of the owner
 * @param caption [Media.Caption.text]
 * @param link official link of the Instagram page of the media
 * @param thumb thumbnail
 * @param dur duration of the video (if it is a video, otherwise null)
 * @param lat latitude
 * @param lng longitude
 * @param status 0=>pending, 1=>failed, 2=>suspended
 */
@Serializable
class Download(
    val id: String,
    val date: Long,
    val url: String,
    val type: Byte,
    val owner: String,
    val caption: String?,
    val link: String,
    val thumb: String?,
    val dur: Float?,
    val lat: Double?,
    val lng: Double?,
    var status: Byte = 0x0
) {

    val fileName: String by lazy { "${owner}_${Utils.fileDateTime(date)}_$id.$ext" }
    val ext: String by lazy {
        if (type != 3.toByte()) {
            var ext = URI(url).path.split(".").last()
            if (ext == "webp" && url.contains("stp=dst-jpg")) ext = "jpg"
            ext
        } else
            "m4a"
    }

    fun isFailed() = status == 1.toByte()

    fun isMainFile() = type.toInt() !in arrayOf(3)

    @Throws(NullPointerException::class)
    fun coordinates() = GpsCoordinates(lat!!, lng!!)

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type
        result = 31 * result + owner.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Download
        return id == other.id && type == other.type &&
            (id != Utils.PROFILE_PHOTO || owner == other.owner)
    }
}
