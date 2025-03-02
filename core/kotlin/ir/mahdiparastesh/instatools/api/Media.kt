package ir.mahdiparastesh.instatools.api

import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
class Media(
    //val can_reply: Boolean?,
    val caption: Caption?,
    val carousel_media: Array<Media>?,
    //val carousel_media_count: Int?,
    //val carousel_media_ids: Array<String>?,
    //val coauthor_producers: Array<User>?,
    val code: String?,
    //val comment_count: Int?,
    val has_audio: Boolean?,
    val has_liked: Boolean?,
    val has_viewer_saved: Boolean?,
    private val id: String, // <media ID>_<user ID>
    //val invited_coauthor_producers: Array<User>?,
    val image_versions2: ImageVersions2,
    private val lat: Double?,
    private val lng: Double?,
    //val like_count: Long?,
    val location: Location?,
    val media_type: Int,
    //val number_of_qualities: Int?,
    //val organic_tracking_token: String?,
    val original_height: Int?, // nullable in tagged carousel items
    val original_width: Int?, // nullable in tagged carousel items
    val owner: User?,
    //val photo_of_you: Boolean?,
    private val pk: String?, // nullable in tagged carousel items
    val product_type: String?,
    val taken_at: Long?, // nullable in tagged carousel items
    val user: User?,
    val video_dash_manifest: String?,
    val video_duration: Float?, // in seconds
    val video_versions: Array<Version>?,
    //val view_count: Long?,
) {

    fun id(): String = pk ?: id.substringBefore("_")

    fun owner(): User = owner ?: user!!

    fun isPost() = product_type in arrayOf("feed", "carousel_container")

    fun link(userName: String? = null) = when (product_type) {
        "feed", "carousel_container" -> Utils.POST_LINK.format(code)
        "clips" -> Utils.REEL_LINK.format(code)
        "story" -> Utils.STORY_LINK.format(userName ?: owner().username, pk)
        // highlights are considered "story" but they don't have unique links of their own,
        // also their Media cannot be distinguished from daily stories!
        null -> nearest(Version.BEST)
        else -> throw IllegalStateException("New product type: $product_type ?!?")
    }

    fun nearest(ideal: Int = Version.BEST, justImage: Boolean = false): String? {
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
        has_audio == true || (carousel_media != null && carousel_media.any { it.media_type == 2 })

    fun audioUrl(): String? {
        if (video_dash_manifest == null) return null
        return video_dash_manifest
            .substringAfter("<AudioChannelConfiguration")
            .substringAfter("<BaseURL")
            .substringAfter(">")
            .substringBefore("</BaseURL>")
    }

    fun latitude(): Double? = lat ?: location?.lat
    fun longitude(): Double? = lng ?: location?.lng

    /**
     * Adds this item to the download queue.
     * @param owner must be specified in stories and highlights.
     * @param onlyOneSlide whether this position of the slider should downloaded, null otherwise
     */
    fun queue(
        idealSize: Int = Version.BEST,
        link: String? = null,
        owner: String? = null,
        onlyOneSlide: Int? = null
    ): ArrayList<Download> {
        val list = arrayListOf<Download>()
        val u = owner ?: owner().username!!
        if (carousel_media != null) for (slide in carousel_media.indices) {
            if (onlyOneSlide != null && onlyOneSlide != slide) continue
            val car = carousel_media[slide]
            list.add(
                Download(
                    car.id(),
                    Utils.compileSecondsTS(car.taken_at ?: taken_at!!),
                    car.nearest(idealSize)!!,
                    car.media_type.toInt().toByte(),
                    u,
                    caption?.text,
                    link ?: link()!!,
                    car.thumb(),
                    car.video_duration,
                    latitude(),
                    longitude(),
                )
            )
        } else list.add(
            Download(
                id(),
                Utils.compileSecondsTS(taken_at!!),
                nearest(idealSize)!!,
                media_type.toInt().toByte(),
                u,
                caption?.text,
                link ?: link()!!,
                thumb(),
                video_duration,
                latitude(),
                longitude(),
            )
        )
        return list
    }


    @Serializable
    class Caption(
        //val created_at: Long,
        //val pk: String?,
        val text: String,
        //val user: User?,
        //val user_id: String?,
    )

    @Serializable
    class ImageVersions2(val candidates: Array<Version>)

    @Serializable
    class Version(
        //val bandwidth: Int?, // in some video_versions, e.g. 1060902
        //val id: String?, // in some video_versions, e.g. "615736350802035v"
        val type: Int?,
        val url: String,
        val height: Int?, // nullable in stories, in that case `type` is provided
        val width: Int?, // --
    ) {
        @Suppress("MemberVisibilityCanBePrivate")
        companion object {
            const val WORST = 0
            const val MEDIUM = -1
            const val BEST = -2
            // Any positive number except these, represents an ideal width,
            // Any negative number except these, represents an ideal height.

            fun pick(
                list: Array<Version>,
                ideal: Int,
                original: Pair<Int, Int>? = null,
            ): String? = when (ideal) {
                BEST -> best(list, original)
                MEDIUM -> medium(list)
                WORST -> worst(list)
                else -> nearest(list, ideal, original)
            }

            fun best(
                list: Array<Version>,
                original: Pair<Int, Int>? = null,
            ): String? {
                if (list[0].width == null)
                    return list.minBy { it.type!! }.url

                var ret: String?
                ret =
                    original?.let { o -> list.find { it.width == o.first && it.height == o.second }?.url }
                if (ret == null) {
                    var maxW = 0
                    var maxH = 0
                    list.forEach {
                        if (it.width!! > maxW) maxW = it.width
                        if (it.height!! > maxH) maxH = it.height
                    }
                    ret = list.find { it.width == maxW && it.height == maxH }?.url
                }
                return ret
            }

            fun medium(list: Array<Version>): String? =
                list.getOrNull(if (list.size <= 1) 0 else list.size / 2)?.url


            fun worst(list: Array<Version>): String? {
                if (list[0].width == null)
                    return list.maxBy { it.type!! }.url

                var minW = 1000
                var minH = 1000
                list.forEach {
                    if (it.width!! < minW) minW = it.width
                    if (it.height!! < minH) minH = it.height
                }
                return list.find { it.width == minW && it.height == minH }?.url
                    ?: list.getOrNull(0)?.url
            }

            fun nearest(
                list: Array<Version>,
                ideal: Int,
                original: Pair<Int, Int>? = null,
            ): String? {
                if (list[0].width == null)
                    return list[0].url

                var nW = original?.first ?: 0
                var nH = original?.second ?: 0
                var nWDif = abs(ideal - nW)
                var nHDif = abs(ideal - nH)
                if (ideal > 0) list.forEach {
                    if (abs(ideal - it.width!!) >= nWDif) return@forEach
                    nWDif = abs(ideal - it.width)
                    nW = it.width
                    nH = it.height!!
                } else list.forEach {
                    val idealH = abs(ideal)
                    if (abs(idealH - it.height!!) >= nHDif) return@forEach
                    nHDif = abs(idealH - it.height)
                    nW = it.height
                    nH = it.width!!
                }
                return list.find { it.width == nW && it.height == nH }?.url
                    ?: list.getOrNull(0)?.url
            }
        }
    }

    @Serializable
    class Location(
        //val address: String?,
        //val city: String?,
        //val external_source: String?,
        //val facebook_places_id: String?,
        //val has_viewer_saved: Boolean?,
        //val is_eligible_for_guides: Boolean?,
        val lat: Double, // actually it's just a Float for now
        val lng: Double,
        //val name: String,
        //val pk: String,
        //val short_name: String?,
        //val profile_pic_url: String?,
    )

    @Serializable
    class Url(val url: String)

    enum class Type(val num: Byte) {
        IMAGE(1),
        VIDEO(2),
        AUDIO(3),
    }
}
