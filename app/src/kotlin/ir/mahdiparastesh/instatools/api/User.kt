package ir.mahdiparastesh.instatools.api

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.view.UiTools.INSTA_PACKAGE
import ir.mahdiparastesh.instatools.view.UiTools.PROFILE
import java.text.DecimalFormat

@Suppress("PropertyName")
data class User(
    //val bio_links: List<BioLink>?,
    val biography: String?,
    val edge_follow: EdgeFollow?, // available via PROFILE_INFO
    val edge_followed_by: EdgeFollow?, // available via PROFILE_INFO
    val followed_by_viewer: Boolean?, // available via PROFILE_INFO
    //val friendship_status: Map<String, Any?>?,
    val full_name: String?,
    val hd_profile_pic_url_info: Media.Version?, // available via USER_INFO (highest quality)
    val hd_profile_pic_versions: List<Media.Version>?, // available via USER_INFO
    val id: String?,
    val is_private: Boolean?,
    //val is_unpublished: Boolean?,
    val pk: String?, // missing in PROFILE_INFO
    val profile_pic_url: String?,
    val profile_pic_url_hd: String?, // available via PROFILE_INFO
    val pronouns: List<String>?,
    val username: String?,
) {

    fun id(): String = id ?: pk!!

    fun visName() = full_name?.ifBlank { username } ?: username

    fun picture(): String = hd_profile_pic_url_info?.url
        ?: hd_profile_pic_versions?.let { list -> Media.Version.best(list) }
        ?: profile_pic_url_hd
        ?: profile_pic_url!!

    fun pv() = is_private == true

    fun favourite(): Favourite = Favourite(id(), username!!, full_name!!, picture(), pv())

    /** Opens an IG profile in Instagram, if Instagram is installed. */
    fun openProfile(c: BaseActivity) {
        try {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(PROFILE.format(username!!)))
                    .setPackage(INSTA_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            Viewer.comeHere(c, id())
        }
    }


    /*data class BioLink(val title: String, val url: String)*/

    /*data class FriendshipStatus(
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

    open class EdgeFollow(val count: Double) {
        override fun toString(): String = when {
            count > 1000000.0 -> DecimalFormat("#.##").format(count / 1000000.0) + "M"
            count > 1000.0 -> DecimalFormat("#.##").format(count / 1000.0) + "K"
            else -> count.toInt().toString()
        } // Cannot move to strings.xml without Context
    }

    //class EdgeFollowMutual(count: Double, val edges: Array<Any>) : EdgeFollow(count)
}
