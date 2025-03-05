package ir.mahdiparastesh.instatools.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class Story(
    val cover_media: Cover?, // null in stories
    //val expiring_at: Long?, // only in feed tray, time in seconds
    //val has_besties_media: Boolean?, // only in feed tray
    val id: String,
    var items: ArrayList<Media>?, // null in highlights tray and feed tray
    //val latest_reel_media: Long, // time in seconds
    //val muted: Boolean?, // null in highlights
    //val ranked_position: Int?, // only in feed tray
    val reel_type: String?, // "user_reel" or "highlight_reel", null in highlights tray
    //val seen: Int?, // null in highlights
    //val seen_ranked_position: Int?, // only in feed tray
    val title: String?, // null in stories
    val user: User,
    //val __typename: String, // only in feed tray, always "XDTReelDict"

    @Transient var opened: Boolean = false,
    @Transient var anSlide: Any? = null
) {

    fun link(): String = when (reel_type) {
        "user_reel" -> "https://www.instagram.com/stories/${user.username}/"
        null, "highlight_reel" ->
            "https://www.instagram.com/stories/highlights/${highlightId()}/" // after "highlight:"

        else -> throw IllegalArgumentException("Unknown story type: $reel_type")
    }

    fun highlightId(): String = id.substring(10)


    @Serializable
    class Cover(
        val cropped_image_version: Media.Url,
        //val full_image_version: Any?
    )

    @Serializable
    class Wrapper(val reels_media: List<Story>)
}