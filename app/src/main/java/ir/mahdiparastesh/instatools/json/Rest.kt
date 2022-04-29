package ir.mahdiparastesh.instatools.json

import android.animation.ObjectAnimator

@Suppress("SpellCheckingInspection")
open class Rest {
    lateinit var status: String

    class User(
        //val account_badges: Array<Map<String, *>>?,
        //val account_type: Float?,
        //val auto_expand_chaining: Boolean?,
        //val biography: String?,
        //val can_be_reported_as_fraud: Boolean?,
        //val creator_shopping_info: Map<String, *>?,
        //val external_url: String?,
        //val fbid_v2: Double?,
        //val follow_friction_type: Float?,
        //val follower_count: Float?,
        //val following_count: Float?,
        //val following_tag_count: Float?,
        //val friendship_status: Friendship?, // INFO lacks this
        val full_name: String?, // INFO lacks this
        //val has_anonymous_profile_picture: Boolean?,
        //val has_guides: Boolean?,
        //val has_highlight_reels: Boolean?,
        //val has_unseen_besties_media: Boolean?,
        //val has_videos: Boolean?,
        //val hd_profile_pic_versions: Array<Media.Candidate>?,
        //val hd_profile_pic_url_info: Media.Candidate?,
        //val highlight_reshare_disabled: Boolean?,
        //val interop_messaging_user_fbid: Double?,
        //val is_bestie: Boolean?,
        //val is_business: Boolean?,
        //val is_call_to_action_enabled: Boolean?,
        //val is_favorite: Boolean?,
        //val is_interest_account: Boolean?,
        //val is_memorialized: Boolean?,
        //val is_potential_business: Boolean?,
        val is_private: Boolean,
        //val is_using_unified_inbox_for_direct: Boolean?,
        //val is_verified: Boolean,
        //val latest_reel_media: Double?,
        //val media_count: Float?,
        //val mutual_followers_count: Float?,
        //val open_external_url_with_in_app_browser: Boolean?,
        val pk: String,
        //val primary_profile_link_type: Float?,
        //val professional_conversion_suggested_account_type: Float?,
        //val profile_context: String?,
        //val profile_context_facepile_users: Array<Any>?,
        //val profile_context_links_with_user_ids: Array<Any>?,
        val profile_pic_url: String,
        //val profile_pic_id: String,
        //val pronouns: Array<Any>?,
        //val reel_auto_archive: String?,
        //val request_contact_enabled: Boolean?,
        //val should_show_category: Boolean,
        //val show_account_transparency_details: Boolean?,
        //val show_fb_link_on_profile: Boolean?,
        //val show_post_insights_entry_point: Boolean?,
        //val total_ar_effects: Float?,
        //val total_igtv_videos: Float?,
        val username: String,
        //val usertags_count: Float?,
        //val wa_addressable: Any?,// Double or Boolean
        //val wa_eligibility: Double?
    ) {
        fun visName() = full_name?.ifBlank { username } ?: username
    }

    class Follow(
        // Both following and followers
        val next_max_id: String? = null,
        val users: Array<User>,
        //val big_list: Boolean,
        //val page_size: Double,
    ) : Rest()

    //class Friendships(val friendship_statuses: Map<String, Friendship>) : Rest()

    /*class Friendship(
        val following: Boolean,
        //val incoming_request: Boolean,
        //val is_bestie: Boolean,
        //val is_feed_favorite: Boolean,
        //val is_private: Boolean,
        //val is_restricted: Boolean,
        //val outgoing_request: Boolean,
    )*/

    class InboxPage(
        //val has_pending_top_requests: Boolean,
        val inbox: Dm.Inbox,
        //val pending_requests_total: Double,
        //val seq_id: Double,
        val viewer: User,
    ) : Rest()

    class InboxThread(val thread: Dm.DmThread) : Rest()

    open class DynamicReelsList : Rest() {
        //var broadcast: Array<Any?>? = null
    }

    class Story(val reel: StoryReel?) : DynamicReelsList()

    interface TrayWrapper<T> where T : Reel {
        val tray: Array<T>
    }

    class Highlights(
        override val tray: Array<HighlightReel>,
        //val show_empty_state: Boolean,
    ) : Rest(), TrayWrapper<HighlightReel>

    class Reels<R>(
        val reels: Map<String, R>,
        val reels_media: Array<R>,
    ) : Rest() where R : Reel

    abstract class Reel(
        //val ad_expiry_timestamp_in_millis: Any?,
        //val can_gif_quick_reply: Boolean,
        //val can_reply: Boolean,
        //val can_reshare: Boolean,
        //val is_cta_sticker_available: Any?,
        var items: Array<Media>?,
        //val latest_reel_media: Double,
        //val reel_type: String,
        //val seen: Double,
        val user: User,
        @Transient var opened: Boolean,
        @Transient var anSlide: ObjectAnimator? = null
    )

    class StoryReel(
        //val expiring_at: Double,
        //val has_besties_media: Boolean?,
        //val has_fan_club_media: Boolean?,
        val id: Double, // User Id not that of the reel
        items: Array<Media>,
        //val media_count: Float,
        //val media_ids: Array<String>,
        //val prefetch_count: Float,
        user: User
    ) : Reel(items, user, true)

    class HighlightReel(
        val cover_media: HighlightCover?, // uncertain "?"
        //val created_at: Double,
        val id: String, // starts with "highlight:"
        //val is_converted_to_clips: Boolean,
        //val is_pinned_highlight: Boolean,
        items: Array<Media>?,
        val media_count: Float,
        //val media_ids: Array<String>?,
        //val prefetch_count: Double,
        //val ranked_position: Double,
        //val seen_ranked_position: Double,
        val title: String,
        user: User
    ) : Reel(items, user, false)

    class HighlightCover(val cropped_image_version: Media.Candidate/*, val crop_rect: Any?*/)

    class Search(
        //val places: Array<HashMap<String, *>>,
        //val hashtags: Array<HashMap<String, *>>,
        //val rank_token: String,
        //val has_more: Boolean,
        val users: Array<ItemUser>,
    ) : Rest()

    class ItemUser(val position: Float, val user: User)

    class Signing/*(val login_nonce: String?)*/ : Rest()

    class DoFollow(
        //val result: String?,
        //val message: String?, // e.g.: "feedback_required"
        val spam: Boolean?,
        //val feedback_title: String?, // e.g.: "Try again later"
        //val feedback_message: String?, // e.g.: "We restrict certain activity to protect our community."
        //val feedback_url: String?,
        //val feedback_action: String?, // e.g.: "report_problem"
    ) : Rest()
}
