package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.Serializable
import java.net.URI

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
 * @param status 0=>pending, 1=>failed, 2=>suspended
 */
@Serializable
class Queued(
    val id: String,
    val date: Long,
    var url: String,
    var type: Byte,
    var owner: String,
    var caption: String?,
    val link: String,
    var thumb: String?,
    var dur: Float?,
    var status: Byte = 0x0
) {
    val fileName: String by lazy { "${owner}_${Utils.fileDateTime(date)}_$id.$ext" }

    val ext: String by lazy { URI(url).path.split(".").last() }

    fun isMainFile() = type.toInt() !in arrayOf(3)

    fun isFailed() = status == 1.toByte()
}
