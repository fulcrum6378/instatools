package ir.mahdiparastesh.instatools.more

import ir.mahdiparastesh.instatools.json.Media

@Suppress("MemberVisibilityCanBePrivate")
abstract class Versioned(
    val image_versions2: Media.ImageVersions2?,
    val original_height: Float?,
    val original_width: Float?,
    val video_versions: Array<Media.VideoVersion>?
) {
    @Suppress("UNCHECKED_CAST")
    fun best(): String {
        var ret: String? = null
        if (video_versions != null)
            ret = bestOfList(video_versions as Array<Media.Candidate>)
        if (ret == null && image_versions2 != null)
            ret = bestOfList(image_versions2.candidates)
        return ret!!
    }

    private fun bestOfList(list: Array<Media.Candidate>): String? {
        var ret: String? = null
        if (original_width != null && original_height != null)
            ret = list.find { it.width == original_width && it.height == original_height }?.url
        if (ret == null) {
            var maxW = 0f
            var maxH = 0f
            list.forEach {
                if (it.width > maxW) maxW = it.width
                if (it.height > maxH) maxH = it.height
            }
            ret = list.find { it.width == maxW && it.height == maxH }?.url
        }
        return ret
    }
}
