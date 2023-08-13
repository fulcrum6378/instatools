package ir.mahdiparastesh.instatools.json

import android.animation.ObjectAnimator

@Suppress("SpellCheckingInspection")
open class Rest {
    lateinit var status: String

    class User(
        //val account_badges: Array<Map<String, *>>?,
        //val all_media_count: Float?,
        //val allowed_commenter_type: String?,
        //val fbid_v2: String?,
        //val friendship_status: Friendship?,
        val full_name: String?,
        //val has_anonymous_profile_picture: Boolean?,
        //val has_highlight_reels: Boolean?,
        //val interop_messaging_user_fbid: Double?,
        //val is_favorite: Boolean?,
        //val is_possible_bad_actor: Map<String, *>?,
        //val is_possible_scammer: Boolean?,
        //val is_potential_business: Boolean?,
        val is_private: Boolean,
        //val is_verified: Boolean,
        //val latest_reel_media: Double?,
        //val mutual_followers_count: Float?,
        //val open_external_url_with_in_app_browser: Boolean?,
        val pk: String,
        //val pk_id: String?,
        val profile_pic_url: String,
        //val profile_pic_id: String,
        //val reel_auto_archive: String?,
        //val text_post_app_joiner_number: Float?, // e.g. 47258519 (for @gracexglenn)
        //val text_post_app_joiner_number_label: String?, // e.g. "47,258,519"
        //val third_party_downloads_enabled: Float?,
        val username: String,
    ) {
        fun visName() = full_name?.ifBlank { username } ?: username
    }

    /** Both following and followers receive this API. */
    class Follow(
        val users: Array<User>? = null,
        /* true for @fulcrum6378 which needs multiple fetches,
         * false for @instatools.apk which requires a single one. */
        //val big_list: Boolean,
        /* Maximum amount of users a single fetch can take which randomly is lower than expected!
         * always equals 200, even in Instagram Web's own fetches! */
        //val page_size: Double,
        val next_max_id: String? = null,
        /* "Accounts you don't follow back", "Least interacted with", etc. ONLY IN FOLLOWERS! */
        //val groups: Map<String, Any?>,
        /* Only in followers */
        //val more_groups_available: Boolean,
        //val has_more: Boolean, always returns false incorrectly!
        //val should_limit_list_of_followers: Boolean,
    ) : Rest()

    class Friendships(val friendship_statuses: Map<String, FriendshipStatus>) : Rest()

    class FriendshipStatus(
        //val blocking: Boolean?, // only in mute/unmute
        //val followed_by: Boolean?, // only in mute/unmute
        val following: Boolean,
        val incoming_request: Boolean?, // only in friendships
        val is_bestie: Boolean,
        val is_blocking_reel: Boolean?, // only in friendships
        //val is_eligible_to_subscribe: Boolean?, // only in mute/unmute
        val is_feed_favorite: Boolean?, // only in friendships
        val is_muting_reel: Boolean?, // only in reels_tray
        val is_private: Boolean?, // only in friendships
        val is_restricted: Boolean?, // only in friendships
        val muting: Boolean?, // only in reels_tray
        val outgoing_request: Boolean,
        //val subscribed: Boolean?, // only in mute/unmute
    )

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
        //val reels_media: Array<R>,
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
        val id: Double, // User Id is the same as that of the reel!
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
        //val feedback_title: String?, // e.g.: "Try again later"
        //val feedback_message: String?, // e.g.: "We restrict certain activity to protect our community."
        //val feedback_url: String?,
        //val feedback_action: String?, // e.g.: "report_problem"
        //val friendship_status: FriendshipStatus,
        //val message: String?, // e.g.: "feedback_required"
        //val previous_following: Boolean?,
        //val result: String?,
        val spam: Boolean?,
    ) : Rest()

    class Seen(val status_code: String /* must be "200" */)

    class ApiFailure(
        //val message: String, // e.g.: "checkpoint_required"
        //val checkpoint_url: String, // e.g.: "https://www.instagram.com/challenge/?next=<THE_API_ENDPOINT>"
        val lock: Boolean,
        //val flow_render_type: Float, // e.g.: 0
    ) : Rest() // was "fail"
}
