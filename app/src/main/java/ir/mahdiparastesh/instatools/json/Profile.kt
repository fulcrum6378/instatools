package ir.mahdiparastesh.instatools.json

@Suppress("unused", "SpellCheckingInspection")
class Profile(
    val always_show_message_button_to_pro_account: Boolean,
    val graphql: GraphQl,
    val logging_page_id: String,
    val profile_pic_edit_sync_props: HashMap<String, *>,
    val seo_category_infos: Array<Array<String>>,
    val show_follow_dialog: Boolean,
    val show_suggested_profiles: Boolean,
    val show_view_shop: Boolean,
    val toast_content_on_load: Any?,
) {
    class GraphQLResponse(val data: GraphQl, status: String) : Rest(status)

    class GraphQl(val user: User)

    class User(
        val biography: String?,
        val blocked_by_viewer: Boolean?,
        val business_address_json: Any?,
        val business_category_name: Any?,
        val business_contact_method: Any?,
        val business_email: Any?,
        val business_phone_number: Any?,
        val category_enum: Any?,
        val category_name: Any?,
        val connected_fb_page: Any?,
        val country_block: Boolean?,
        val edge_felix_video_timeline: HashMap<String?, *>?,
        val edge_follow: HashMap<String?, *>?,
        val edge_followed_by: FollowedBy,
        val edge_media_collections: HashMap<String?, *>?,
        val edge_mutual_followed_by: HashMap<String?, *>?,
        val edge_owner_to_timeline_media: TimelineMedia?,
        val edge_saved_media: HashMap<String?, *>?,
        val external_url: Any?,
        val external_url_linkshimmed: Any?,
        val fbid: String?,
        val followed_by_viewer: Boolean?,
        val follows_viewer: Boolean?,
        val full_name: String,
        val has_ar_effects: Boolean?,
        val has_blocked_viewer: Boolean?,
        val has_channel: Boolean?,
        val has_clips: Boolean?,
        val has_guides: Boolean?,
        val has_requested_viewer: Boolean?,
        val hide_like_and_view_counts: Boolean?,
        val highlight_reel_count: Float?,
        val id: String, // The same as Rest.User.pk
        val is_business_account: Boolean?,
        val is_embeds_disabled: Boolean?,
        val is_joined_recently: Boolean?,
        val is_private: Boolean?,
        val is_professional_account: Boolean?,
        val is_verified: Boolean?,
        val overall_category_name: Any?,
        val profile_pic_url: String?,
        val profile_pic_url_hd: String?,
        val pronouns: Array<Any>?,
        val requested_by_viewer: Boolean?,
        val restricted_by_viewer: Boolean?,
        val should_show_category: Boolean?,
        val should_show_public_contacts: Boolean?,
        val username: String
    )

    class TimelineMedia(
        val page_info: PageInfo,
        val count: Float,
        val edges: Array<HashMap<String, *>>
    )

    class PageInfo(val has_next_page: Boolean, val end_cursor: String)

    class FollowedBy(val count: Float)
}
