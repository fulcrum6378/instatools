package ir.mahdiparastesh.instatools.json

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
}
