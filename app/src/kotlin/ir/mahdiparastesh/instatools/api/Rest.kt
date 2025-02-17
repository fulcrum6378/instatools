package ir.mahdiparastesh.instatools.api

import android.animation.ObjectAnimator

@Suppress("SpellCheckingInspection")
open class Rest {
    lateinit var status: String

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
    )

    class UserInfo(
        //val recs_from_friends: Map<String, *>?,
        val user: User,
    ) : Rest()

    class InboxPage(
        //val has_pending_top_requests: Boolean,
        val inbox: Dm.Inbox,
        //val pending_requests_total: Double,
        //val seq_id: Double,
        //val viewer: User,
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
        //val id: Double, // User Id is the same as that of the reel!
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

    class HighlightCover(val cropped_image_version: Media.Version/*, val crop_rect: Any?*/)

    class Search(
        //val places: Array<HashMap<String, *>>,
        //val hashtags: Array<HashMap<String, *>>,
        //val rank_token: String,
        //val has_more: Boolean,
        val users: Array<ItemUser>,
    ) : Rest()

    class ItemUser(/*val position: Float, */val user: User)

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
}
