package ir.mahdiparastesh.instatools.json

/**
 * Any IG post or reel that contains Audio must implement this in order for the audio file to be
 * able to be downloaded.
 */
@Suppress("PropertyName")
interface Audible {
    val is_dash_eligible: Any? // sometimes boolean sometimes double(0|1)
    val video_dash_manifest: String?
    val number_of_qualities: Float?

    fun audioUrl(): String? {
        if (video_dash_manifest == null) return null
        return video_dash_manifest!!
            .substringAfter("<AudioChannelConfiguration")
            .substringAfter("<BaseURL")
            .substringAfter(">")
            .substringBefore("</BaseURL>")
    }
    // M4A ought not to be stored as MP3!
    // https://www.veed.io/convert/m4a-to-mp3
}
