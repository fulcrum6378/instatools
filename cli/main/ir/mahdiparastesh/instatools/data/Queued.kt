package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.util.Utils
import java.net.URI

/** Data structure for information of a media. */
data class Queued(
    val id: String,
    val date: Long,
    val url: String,
    val type: Byte,
    val owner: String,
    val caption: String?,
    val link: String?,
    //val thumb: String?,
) {
    fun fileName(ext: String) = "${owner}_${Utils.fileDateTime(date)}_$id.$ext"

    fun extension() = URI(url).path.split(".").last()
}
