package ir.mahdiparastesh.instatools.data

import com.ashampoo.kim.model.GpsCoordinates
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.job.Queuer
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.Serializable
import java.net.URI
import kotlin.jvm.Throws

/**
 * @param id [Media.pk]
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
    override val id: String,
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
    override var status: Byte = 0x0
) : Queuer.Queued {
    override val fileName: String by lazy { "${owner}_${Utils.fileDateTime(date)}_$id.$ext" }

    val ext: String by lazy { URI(url).path.split(".").last() }

    fun isMainFile() = type.toInt() !in arrayOf(3)

    @Throws(NullPointerException::class)
    fun coordinates() = GpsCoordinates(lat!!, lng!!)
}
