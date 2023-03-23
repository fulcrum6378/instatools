package ir.mahdiparastesh.instatools.json

import ir.mahdiparastesh.instatools.data.Favourite
import java.text.DecimalFormat
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")
class GraphQl(val data: GraphQlData) : Rest() {

    class GraphQlData(
        val user: User?,
        val xdt_api__v1__media__shortcode__web_info: MediaShortcodeWebInfo,
    )

    /*class Profile(
        //val always_show_message_button_to_pro_account: Boolean,
        //val graphql: GraphQlData?,
        //val logging_page_id: String,
        //val profile_pic_edit_sync_props: Map<String, *>,
        //val seo_category_infos: Array<Array<String>>,
        //val show_follow_dialog: Boolean,
        //val show_suggested_profiles: Boolean,
        //val show_view_shop: Boolean,
        //val toast_content_on_load: Any?,
    )*/

    class User(
        //val biography: String?,
        //val blocked_by_viewer: Boolean?,
        //val business_address_json: Any?,
        //val business_category_name: Any?,
        //val business_contact_method: Any?,
        //val business_email: Any?,
        //val business_phone_number: Any?,
        //val category_enum: Any?,
        //val category_name: Any?,
        //val connected_fb_page: Any?,
        //val country_block: Boolean?,
        //val edge_felix_video_timeline: EdgeList?, // Useless "VIDEOS" tab
        val edge_follow: EdgeFollow?,
        val edge_followed_by: EdgeFollow?,
        //val edge_media_collections: EdgeList?,
        //val edge_mutual_followed_by: Map<String?, *>?,
        val edge_owner_to_timeline_media: EdgeList?, // Main posts
        //val edge_saved_media: EdgeList?, // Saved Posts
        //val external_url: Any?,
        //val external_url_linkshimmed: Any?,
        //val fbid: String?,
        val followed_by_viewer: Boolean?,
        //val follows_viewer: Boolean?,
        val full_name: String,
        //val has_ar_effects: Boolean?,
        //val has_blocked_viewer: Boolean?,
        //val has_channel: Boolean?,
        //val has_clips: Boolean?,
        //val has_guides: Boolean?,
        //val has_requested_viewer: Boolean?,
        //val hide_like_and_view_counts: Boolean?,
        //val highlight_reel_count: Double?,
        val id: String, // The same as Rest.User.pk
        //val is_business_account: Boolean?,
        //val is_embeds_disabled: Boolean?,
        //val is_joined_recently: Boolean?,
        val is_private: Boolean?,
        //val is_professional_account: Boolean?,
        //val is_verified: Boolean?,
        //val overall_category_name: Any?,
        val profile_pic_url: String?,
        val profile_pic_url_hd: String?,
        //val pronouns: Array<Any>?,
        //val requested_by_viewer: Boolean?,
        //val restricted_by_viewer: Boolean?,
        //val should_show_category: Boolean?,
        //val should_show_public_contacts: Boolean?,
        val username: String
    ) {
        fun photo() = profile_pic_url_hd ?: profile_pic_url

        fun favourite(): Favourite = Favourite(id, username, full_name, profile_pic_url, pv())

        fun pv() = is_private == true

        fun access() = !pv() || followed_by_viewer == true

        fun edges() = edge_owner_to_timeline_media?.edges

        fun hasMore(): Boolean =
            edge_owner_to_timeline_media?.let { it.edges.size < it.count.toInt() } ?: false
    }

    open class EdgeFollow(val count: Double) {
        override fun toString(): String = when {
            count > 1000000.0 -> DecimalFormat("#.##").format(count / 1000000.0) + "M"
            count > 1000.0 -> DecimalFormat("#.##").format(count / 1000.0) + "K"
            else -> count.toInt().toString()
        } // Cannot move to strings.xml without Context
    }

    //class EdgeFollowMutual(count: Double, val edges: Array<Any>) : EdgeFollow(count)

    class EdgeList(
        var page_info: PageInfo,
        var count: Double,
        var edges: CopyOnWriteArrayList<EdgePost>
    ) {
        fun hiddenItems() = count.toInt() - edges.size
    }

    class PageInfo(val has_next_page: Boolean, val end_cursor: String)

    class EdgePost(val node: Post)

    class Post(
        val __typename: String,
        //val accessibility_caption: String?,
        //val clips_music_attribution_info: Any?,
        //val coauthor_producers: Array<Any>,
        //val comments_disabled: Boolean,
        //val dash_info: DashInfo?,
        //val display_url: String,
        //val dimensions: Map<String, Double>,
        //val edge_liked_by: Map<String, Double>,
        //val edge_media_preview_like: Map<String, *>,
        //val edge_media_to_caption: EdgesCaption,
        //val edge_media_to_comment: Map<String, *>,
        //val edge_media_to_tagged_user: EdgeTaggedUsers,
        val edge_sidecar_to_children: EdgeSlides?,
        //val fact_check_information: Any?,
        //val fact_check_overall_rating: Any?,
        //val felix_profile_grid_crop: Any?,
        //val gating_info: Any?,
        //val has_audio: Boolean,
        //val has_upcoming_event: Boolean,
        val id: String,
        //val is_video: Boolean,
        //val location: Location?,
        //val media_overlay_info: Any?,
        //val media_preview: Any?,
        val owner: Owner,
        //val product_type: String,
        //val sharing_friction_info: Map<String, *>,
        val shortcode: String,
        //val taken_at_timestamp: Double,
        val thumbnail_resources: Array<Src>?, // nullable in SAVED but not SAVED_FIRST
        val thumbnail_src: String, // biggest thumbnail
        //val tracking_token: String,
        //val video_url: String,
        //val video_view_count: Double,
    )

    class Owner(val id: String, val username: String)

    //class EdgesCaption(val edges: Array<EdgeCaption>)

    //class EdgeCaption(val node: Caption)

    //class Caption(val text: String)

    class Src(val src: String, val config_width: Double/*, val config_height: Double*/)

    class EdgeSlides(val edges: Array<EdgeSlide>)

    class EdgeSlide(val node: Slide)

    class Slide(
        //val __typename: String,
        val id: String,
        //val gating_info: Any?,
        //val fact_check_overall_rating: Any?,
        //val fact_check_information: Any?,
        //val media_overlay_info: Any?,
        //val sensitivity_friction_info: Any?,
        //val sharing_friction_info: Map<String, *>?,
        //val dimensions: Map<String, Double>?,
        //val display_url: String, // USE THIS
        //val display_resources: Array<Src>
    )

    /*class DashInfo(
        override val is_dash_eligible: Any?,
        override val video_dash_manifest: String?,
        override val number_of_qualities: Float?
    ) : Audible*/

    class MediaShortcodeWebInfo(val items: List<Media>)
}
