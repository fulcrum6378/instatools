package ir.mahdiparastesh.instatools.json

import kotlin.math.abs

@Suppress("UNCHECKED_CAST")
abstract class Versioned(
    val image_versions2: Media.ImageVersions2?,
    private val original_height: Float?,
    private val original_width: Float?,
    val video_versions: Array<Media.VideoVersion>?,
    var carousel_media: Array<Media.CarouselMedia>?
) {
    companion object {
        const val WORST = 0f
        const val MEDIUM = -1f
        const val BEST = -2f
        // Any positive number except these represents an ideal width,
        // Any negative number except these represents an ideal height.
    }

    fun nearest(ideal: Float = BEST, justImage: Boolean = false): String? {
        var ret: String? = null
        if (!justImage && video_versions != null)
            ret = funChooser(video_versions as Array<Media.Candidate>, ideal)
        if (ret == null && image_versions2 != null)
            ret = funChooser(image_versions2.candidates, ideal)
        return ret
    }

    private fun funChooser(list: Array<Media.Candidate>, ideal: Float): String? = when (ideal) {
        BEST -> bestOfList(list)
        MEDIUM -> mediumOfList(list)
        WORST -> worstOfList(list)
        else -> nearestOfList(list, ideal)
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

    private fun nearestOfList(list: Array<Media.Candidate>, ideal: Float): String? {
        if (original_width == null || original_height == null) return null
        var nW = original_width
        var nH = original_height
        var nWDif = abs(ideal - nW)
        var nHDif = abs(ideal - nH)
        if (ideal > 0) list.forEach {
            if (abs(ideal - it.width) >= nWDif) return@forEach
            nWDif = abs(ideal - it.width)
            nW = it.width
            nH = it.height
        } else list.forEach {
            val idealH = abs(ideal)
            if (abs(idealH - it.height) >= nHDif) return@forEach
            nHDif = abs(idealH - it.height)
            nW = it.height
            nH = it.width
        }
        return list.find { it.width == nW && it.height == nH }?.url
            ?: list.getOrNull(0)?.url
    }

    private fun mediumOfList(list: Array<Media.Candidate>): String? =
        list.getOrNull(if (list.size <= 1) 0 else list.size / 2)?.url


    private fun worstOfList(list: Array<Media.Candidate>): String? {
        var minW = 1000f
        var minH = 1000f
        list.forEach {
            if (it.width < minW) minW = it.width
            if (it.height < minH) minH = it.height
        }
        return list.find { it.width == minW && it.height == minH }?.url
            ?: list.getOrNull(0)?.url
    }

    fun thumb() = //(this as Media).thumbnails?.sprite_urls?.getOrNull(0)
        (if (this is Media) carousel_media?.getOrNull(0)?.nearest(WORST, true) else null)
            ?: nearest(WORST, true)
}
