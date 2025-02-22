package ir.mahdiparastesh.instatools.api

@Suppress("MemberVisibilityCanBePrivate", "PropertyName")
class User(
    //val bio_links: Array<BioLink>?,
    val biography: String?,
    //val friendship_status: Map<String, Any?>?,
    val full_name: String?,
    val hd_profile_pic_url_info: Media.Version?, // available via USER_INFO (highest quality)
    val hd_profile_pic_versions: Array<Media.Version>?, // available via USER_INFO
    val id: String?,
    val is_private: Boolean?,
    //val is_unpublished: Boolean?,
    val pk: String?,
    val profile_pic_url: String?,
    val profile_pic_url_hd: String?, // available via PROFILE_INFO
    val pronouns: Array<String>?,
    val username: String?,
) {

    fun id(): String = id ?: pk!!

    fun visName() = full_name?.ifBlank { username } ?: username

    fun picture() = hd_profile_pic_url_info?.url
        ?: hd_profile_pic_versions?.let { list -> Media.Version.best(list) }
        ?: profile_pic_url_hd
        ?: profile_pic_url


    /*class BioLink(val title: String, val url: String)*/

    /*class FriendshipStatus(
        //val blocking: Boolean?, // only in mute/unmute and show(one)
        //val followed_by: Boolean?, // only in mute/unmute and show(one)
        //val following: Boolean,
        //val incoming_request: Boolean?, // only in show_many and show(one)
        val is_bestie: Boolean,
        //val is_blocking_reel: Boolean?, // only in mute/unmute and show(one)
        //val is_eligible_to_subscribe: Boolean?, // only in mute/unmute and show(one)
        val is_feed_favorite: Boolean?, // only in show_many and mute/unmute and show(one)
        //val is_guardian_of_viewer: Boolean?, // only in show(one)
        //val is_muting_notes: Boolean?, // only in show(one)
        //val is_muting_reel: Boolean?, // only in reels_tray and mute/unmute and show(one)
        //val is_private: Boolean?, // only in show_many and mute/unmute and show(one)
        val is_restricted: Boolean?, // only in show_many and mute/unmute and show(one)
        //val is_supervised_by_viewer: Boolean?, // only in show(one)
        //val muting: Boolean?, // only in reels_tray and mute/unmute and show(one)
        //val outgoing_request: Boolean,
        //val status: Boolean?, // as Rest, only in show(one)
        //val subscribed: Boolean?, // only in mute/unmute and show(one)
    )*/
}
