package ir.mahdiparastesh.instatools.json

@Suppress("SpellCheckingInspection", "unused")
open class Rest(val status: String) {

    class User(
        val account_badges: Array<Map<String, *>>,
        val fbid_v2: Double,
        val friendship_status: Friendship,
        val full_name: String,
        val has_anonymous_profile_picture: Boolean,
        val has_highlight_reels: Boolean,
        val interop_messaging_user_fbid: Double?,
        val is_private: Boolean,
        val is_using_unified_inbox_for_direct: Boolean?,
        val is_verified: Boolean,
        val latest_reel_media: Double,
        val pk: String,
        val profile_pic_url: String,
        val profile_pic_id: String,
        val reel_auto_archive: String?,
        val should_show_category: Boolean,
        val username: String,
        val wa_addressable: Any?,// Double or Boolean
        val wa_eligibility: Double?
    )

    class Follow( // Both following and followers
        val next_max_id: String? = null,
        val users: Array<User>,
        val big_list: Boolean,
        val page_size: Double,
        status: String
    ) : Rest(status)

    class Friendships(val friendship_statuses: Map<String, Friendship>, status: String) :
        Rest(status)

    class Friendship(
        val following: Boolean,
        val incoming_request: Boolean,
        val is_bestie: Boolean,
        val is_feed_favorite: Boolean,
        val is_private: Boolean,
        val is_restricted: Boolean,
        val outgoing_request: Boolean,
    )

    class InboxPage(
        val has_pending_top_requests: Boolean,
        val inbox: Dm.Inbox,
        val pending_requests_total: Double,
        val seq_id: Double,
        val viewer: User,
        status: String
    ) : Rest(status)
}
