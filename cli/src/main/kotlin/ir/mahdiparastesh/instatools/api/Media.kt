package ir.mahdiparastesh.instatools.api

import ir.mahdiparastesh.instatools.util.Utils
import kotlin.math.abs

@Suppress("MemberVisibilityCanBePrivate", "PropertyName")
class Media(
    //val can_reply: Boolean?,
    val caption: Caption?,
    val carousel_media: Array<Media>?,
    //val carousel_media_count: Float?,
    //val carousel_media_ids: Array<String>?,
    //val coauthor_producers: Array<User>?,
    val code: String?,
    //val comment_count: Float?,
    val has_audio: Boolean?,
    val has_liked: Boolean?,
    val has_viewer_saved: Boolean?,
    val id: String, // <media ID>_<user ID>
    //val invited_coauthor_producers: Array<User>?,
    val image_versions2: ImageVersions2,
    //val like_count: Double?,
    //val location: Map<String, Any?>?,
    val media_type: Float,
    //val number_of_qualities: Float?,
    //val organic_tracking_token: String?,
    val original_height: Float?, // nullable in tagged carousel items
    val original_width: Float?, // nullable in tagged carousel items
    val owner: User?,
    //val photo_of_you: Boolean?,
    val pk: String?, // nullable in tagged carousel items
    val product_type: String?,
    val taken_at: Double,
    val user: User?,
    val video_dash_manifest: String?,
    val video_versions: Array<Version>?,
    //val view_count: Double?,
) {

    fun pk() = pk ?: id.substringBefore("_")

    fun owner(): User = owner ?: user!!

    fun link(userName: String? = null) = when (product_type) {
        "feed", "carousel_container" -> Utils.POST_LINK.format(code)
        "clips" -> Utils.REEL_LINK.format(code)
        "story" -> Utils.STORY_LINK.format(userName ?: owner().username, pk)
        // highlights are considered "story" but they don't have unique links of their own,
        // also their Media cannot be distinguished from daily stories!
        null -> nearest(Version.BEST)
        else -> throw IllegalStateException("New product type: $product_type ?!?")
    }

    fun nearest(ideal: Float = Version.BEST, justImage: Boolean = false): String? {
        var ret: String? = null
        val original = original_width?.let { Pair(original_width, original_height!!) }
        if (!justImage && video_versions != null)
            ret = Version.pick(video_versions, ideal, original)
        if (ret == null)
            ret = Version.pick(image_versions2.candidates, ideal, original)
        return ret
    }

    fun thumb() =
        carousel_media?.getOrNull(0)?.nearest(Version.WORST, true)
            ?: nearest(Version.WORST, true)

    fun hasAudio() =
        has_audio == true || (carousel_media != null && carousel_media.any { it.media_type == 2f })

    fun audioUrl(): String? {
        if (video_dash_manifest == null) return null
        return video_dash_manifest
            .substringAfter("<AudioChannelConfiguration")
            .substringAfter("<BaseURL")
            .substringAfter(">")
            .substringBefore("</BaseURL>")
    }


    class Caption(
        //val created_at: Double,
        val pk: String,
        val text: String,
        val user: User?,
        //val user_id: String?,
    )

    class ImageVersions2(val candidates: Array<Version>)

    class Version(
        val url: String,
        val height: Float,
        val width: Float,
    ) {
        @Suppress("MemberVisibilityCanBePrivate")
        companion object {
            const val WORST = 0f
            const val MEDIUM = -1f
            const val BEST = -2f
            // Any positive number except these, represents an ideal width,
            // Any negative number except these, represents an ideal height.

            const val THUMB = -3f  // used only in Exporter

            fun pick(
                list: Array<Version>,
                ideal: Float,
                original: Pair<Float, Float>? = null,
            ): String? = when (ideal) {
                BEST -> best(list, original)
                MEDIUM -> medium(list)
                WORST -> worst(list)
                else -> nearest(list, ideal, original)
            }

            fun best(
                list: Array<Version>,
                original: Pair<Float, Float>? = null,
            ): String? {
                var ret: String?
                ret = original?.let { o -> list.find { it.width == o.first && it.height == o.second }?.url }
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

            fun medium(list: Array<Version>): String? =
                list.getOrNull(if (list.size <= 1) 0 else list.size / 2)?.url


            fun worst(list: Array<Version>): String? {
                var minW = 1000f
                var minH = 1000f
                list.forEach {
                    if (it.width < minW) minW = it.width
                    if (it.height < minH) minH = it.height
                }
                return list.find { it.width == minW && it.height == minH }?.url
                    ?: list.getOrNull(0)?.url
            }

            fun nearest(
                list: Array<Version>,
                ideal: Float,
                original: Pair<Float, Float>? = null,
            ): String? {
                var nW = original?.first ?: 0f
                var nH = original?.second ?: 0f
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
        }
    }

    @Suppress("unused")
    enum class Type(val num: Byte) {
        IMAGE(1),
        VIDEO(2),
        AUDIO(3),
    }
}
