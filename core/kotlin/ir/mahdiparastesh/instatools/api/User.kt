package ir.mahdiparastesh.instatools.api

import kotlinx.serialization.Serializable
import java.text.DecimalFormat

@Serializable
class User(
    //val bio_links: Array<BioLink>?,
    val biography: String?,
    val edge_follow: ProfileEdge?, // available via PROFILE_INFO
    val edge_followed_by: ProfileEdge?, // available via PROFILE_INFO
    val edge_owner_to_timeline_media: ProfileEdge?, // available via PROFILE_INFO
    val followed_by_viewer: Boolean?, // available via PROFILE_INFO
    //val friendship_status: FriendshipStatus?,
    val full_name: String?,
    val hd_profile_pic_url_info: Media.Url?, // available via USER_INFO (highest quality)
    val hd_profile_pic_versions: Array<Media.Version>?, // available via USER_INFO
    val id: String?,
    val is_private: Boolean?,
    //val is_unpublished: Boolean?,
    val pk: String?, // missing in PROFILE_INFO
    val profile_pic_url: String?,
    val profile_pic_url_hd: String?, // available via PROFILE_INFO
    val pronouns: Array<String>?,
    val username: String?,
) {

    fun id(): String = id ?: pk!!

    fun originalPicture(): String = hd_profile_pic_url_info?.url
        ?: hd_profile_pic_versions?.let { list -> Media.Version.best(list) }
        ?: profile_pic_url_hd
        ?: profile_pic_url!!

    fun pv() = is_private == true


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

    @Serializable
    class ProfileEdge(val count: Long) {
        override fun toString(): String = when {
            count > 1000000 -> DecimalFormat("#.##").format(count / 1000000) + "M"
            count > 1000 -> DecimalFormat("#.##").format(count / 1000) + "K"
            else -> count.toInt().toString()
        } // Cannot move to strings.xml without Context
    }

    //class EdgeFollowMutual(count: Long, val edges: Array<Any>) : EdgeFollow(count)
}
