package ir.mahdiparastesh.instatools.api

import ir.mahdiparastesh.instatools.util.CopyOnWriteArrayListSerializer
import kotlinx.serialization.Serializable
import java.util.concurrent.CopyOnWriteArrayList

/** Template for a REST API response (belonging to the old Instagram API) */
interface Rest {
    val status: String

    @Serializable
    class QuickResponse(override val status: String) : Rest

    @Serializable
    class LazyList<N>(
        //val auto_load_more_enabled: Boolean,
        @Serializable(with = CopyOnWriteArrayListSerializer::class)
        val items: CopyOnWriteArrayList<N>,
        var more_available: Boolean,
        var next_max_id: String?,
        //val num_results: Float, // in current fetch, not real total
        override val status: String,
    ) : Rest

    @Serializable
    class UserInfo(
        val user: User,
        override val status: String
    ) : Rest


    /* Both following and followers receive this API. */
    /*@Serializable
    class Follow(
        val users: Array<User>? = null,
        // true for @fulcrum6378 which needs multiple fetches,
        // false for @instatools.apk which requires a single one.
        //val big_list: Boolean,
        // Maximum amount of users a single fetch can take which randomly is lower than expected!
        // always equals 200, even in Instagram Web's own fetches!
        //val page_size: Int,
        val next_max_id: String? = null,
        // "Accounts you don't follow back", "Least interacted with", etc. ONLY IN FOLLOWERS!
        //val groups: Map<String, Any?>,
        // Only in followers
        //val more_groups_available: Boolean,
        //val has_more: Boolean, always returns false incorrectly!
        //val should_limit_list_of_followers: Boolean,
        override val status: String
    ) : Rest*/

    /*@Serializable
    class Friendships(
        val friendship_statuses: Map<String, FriendshipStatus>,
        override val status: String
    ) : Rest*/

    /*@Serializable
    class FriendshipStatus(
        //val blocking: Boolean?, // only in mute/unmute and show(one)
        //val followed_by: Boolean?, // only in mute/unmute and show(one)
        //val following: Boolean,
        //val incoming_request: Boolean?, // only in show_many and show(one)
        //val is_bestie: Boolean,
        //val is_blocking_reel: Boolean?, // only in mute/unmute and show(one)
        //val is_eligible_to_subscribe: Boolean?, // only in mute/unmute and show(one)
        //val is_feed_favorite: Boolean?, // only in show_many and mute/unmute and show(one)
        //val is_guardian_of_viewer: Boolean?, // only in show(one)
        //val is_muting_notes: Boolean?, // only in show(one)
        //val is_muting_reel: Boolean?, // only in reels_tray and mute/unmute and show(one)
        //val is_private: Boolean?, // only in show_many and mute/unmute and show(one)
        //val is_restricted: Boolean?, // only in show_many and mute/unmute and show(one)
        //val is_supervised_by_viewer: Boolean?, // only in show(one)
        //val muting: Boolean?, // only in reels_tray and mute/unmute and show(one)
        //val outgoing_request: Boolean,
        //val status: Boolean?, // as Rest, only in show(one)
        //val subscribed: Boolean?, // only in mute/unmute and show(one)
    )*/
}
