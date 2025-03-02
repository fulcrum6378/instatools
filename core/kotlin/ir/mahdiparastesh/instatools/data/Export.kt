package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.Dm
import ir.mahdiparastesh.instatools.api.Media
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI

@Serializable
data class Export(
    val thread: Dm.DmThread,
    val method: Byte,
    val firstFetchDate: Long,
    val filters: Filters,
    val media: HashMap<String, Attachment> = hashMapOf(),
) {

    @Serializable
    data class Filters(
        var image: Int? = null,
        var video: Int? = null,
        var post: Int = Media.Version.THUMB,
        var reel: Int = Media.Version.THUMB,
        var story: Int = Media.Version.THUMB,
        var uploadedImage: Int = Media.Version.BEST,
        var uploadedVideo: Int = Media.Version.BEST,
        var voice: Boolean = true,
        var minDate: Long? = null,
        var maxDate: Long? = null,
    )

    @Serializable
    class Attachment(
        val url: String,
        val type: Int,
        var cachePath: String,
    ) {
        constructor(url: String, type: Int, folder: File, dmId: String, quality: Int) :
            this(url, type, "${dmId}_$quality.${URI(url).path.split(".").last()}")

        fun ext() = URI(url).path.split(".").last()

        fun fileName(dmId: String) = "${dmId}.${ext()}"
    }

    companion object {
        const val METHOD_JSON: Byte = 1
        const val METHOD_TEXT: Byte = 2
        const val METHOD_HTML: Byte = 3
        const val METHOD_PDF: Byte = 4
    }
}
