package ir.mahdiparastesh.instatools.api

import java.util.concurrent.CopyOnWriteArrayList

interface Rest {
    val status: String

    data class QuickResponse(override val status: String) : Rest

    data class LazyList<N>(
        //val auto_load_more_enabled: Boolean,
        val items: CopyOnWriteArrayList<N>,
        var more_available: Boolean,
        var next_max_id: String?,
        //val num_results: Float, // in current fetch, not real total
        override val status: String,
    ) : Rest

    data class SavedItem(val media: Media)

    data class UserInfo(
        val user: User,
        override val status: String
    ) : Rest

    class InboxPage(
        //val has_pending_top_requests: Boolean,
        val inbox: Dm.Inbox,
        //val pending_requests_total: Double,
        //val seq_id: Double,
        //val viewer: User,
        override val status: String
    ) : Rest

    class InboxThread(
        val thread: Dm.DmThread,
        override val status: String
    ) : Rest


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
        override val status: String
    ) : Rest

    class Friendships(
        val friendship_statuses: Map<String, FriendshipStatus>,
        override val status: String
    ) : Rest

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

    class Search(
        //val places: Array<HashMap<String, *>>,
        //val hashtags: Array<HashMap<String, *>>,
        //val rank_token: String,
        //val has_more: Boolean,
        val users: Array<ItemUser>,
        override val status: String
    ) : Rest

    class ItemUser(/*val position: Float, */val user: User)

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
        override val status: String
    ) : Rest

    class Seen(val status_code: String /* must be "200" */)
}
