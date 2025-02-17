package ir.mahdiparastesh.instatools.api

@Suppress("PropertyName")
data class Story(
    //val cover_media: Cover?, // null in stories
    val id: String, // user id
    var items: List<Media>?, // null in highlights tray
    //val latest_reel_media: Double, // time in seconds
    //val muted: Boolean?, // null in highlights
    val reel_type: String?, // "user_reel" or "highlight_reel", null in highlights tray
    //val seen: Float?, // null in highlights
    val title: String?, // null in stories
    val user: User
) {

    fun link(): String = when (reel_type) {
        "user_reel" -> "https://www.instagram.com/stories/${user.username}/"
        null, "highlight_reel" ->
            "https://www.instagram.com/stories/highlights/${highlightId()}/" // after "highlight:"

        else -> throw IllegalArgumentException("Unknown story type: $reel_type")
    }

    fun highlightId(): String = id.substring(10)


    /*data class Cover(
        val cropped_image_version: Url,
        val full_image_version: Any?
    )*/

    /*data class Url(val url: String)*/

    data class Wrapper(val reels_media: List<Story>)
}