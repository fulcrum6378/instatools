package ir.mahdiparastesh.instatools.json

@Suppress("SpellCheckingInspection", "unused")
class Dm(
    val client_context: String,
    val is_sent_by_viewer: Boolean,
    val is_shh_mode: Boolean,
    val item_id: String,
    val item_type: String,
    val preview_medias: Array<Any?>,
    val reactions: Reactions,
    val show_forward_attribution: Boolean,
    val timestamp: Double,
    val user_id: Double,

    // Item Types
    val clip: Media?,
    val felix_share: Media?,
    val link: Map<String, Any?>?,
    val media: Map<String, Any?>?,
    val profile: Rest.User?,
    val story_share: StoryShare?,
    val text: String?,
) {
    class Inbox(
        val blended_inbox_enabled: Boolean,
        val has_older: Boolean,
        val next_cursor: ContinuumCursor,
        val prev_cursor: ContinuumCursor,
        val threads: Array<DmThread>,
        val unseen_count: Double,
        val unseen_count_ts: Double,
    )

    class ContinuumCursor(// each are either Double or String
        val cursor_timestamp_seconds: Any,
        val cursor_relevancy_score: Any,
        val cursor_thread_v2_id: Any,
    )

    class DmThread(
        //val admin_user_ids: Array<Any?>,
        //val approval_required_for_new_members: Boolean,
        val archived: Boolean,
        val assigned_admin_id: Double,
        //val bc_partnership: Boolean,
        val business_thread_folder: Double,
        //val canonical: Boolean,
        val encoded_server_data_info: String,
        val folder: Double,
        val group_link_joinable_mode: Double,
        val has_groups_xac_ineligible_user: Boolean,
        val has_newer: Boolean,
        val has_older: Boolean,
        //val input_mode: Double,
        //val inviter: Rest.User,
        //val is_close_friend_thread: Boolean,
        //val is_fanclub_subscriber_thread: Boolean,
        val is_group: Boolean,
        val is_translation_enabled: Boolean,
        val is_xac_thread: Boolean,
        val items: Array<Dm>,
        //val joinable_group_link: String,
        val last_activity_at: Double,
        val last_non_sender_item_at: Double,
        val last_permanent_item: Dm,
        val last_seen_at: Map<String, Map<String, Any?>>,
        //val left_users: Array<Any>,
        //val marked_as_unread: Boolean,
        //val mentions_muted: Boolean,
        //val muted: Boolean,
        val named: String,
        val newest_cursor: String,
        val next_cursor: String,
        val oldest_cursor: String,
        //val pending: Boolean,
        //val pending_user_ids: Array<Any?>,
        val prev_cursor: String,
        //val read_state: Double,
        //val relevancy_score: Double,
        //val relevancy_score_expr: Double,
        //val rtc_feature_set_str: String,
        //val shh_mode_enabled: Boolean,
        //val shh_replay_enabled: Boolean,
        //val shh_toggler_userid: Any?,
        //val spam: Boolean,
        //val system_folder: Double,
        //val theme: Map<String, String>,
        //val thread_context_items: Any?,
        //val thread_has_audio_only_call: Boolean,
        //val thread_has_drop_in: Boolean,
        val thread_id: String,
        val thread_image: Any?,
        val thread_label: Double,
        //val thread_languages: Map<String, String>,
        val thread_title: String,
        val thread_type: String,
        val thread_v2_id: String,
        //val translation_banner_impression_count: Double,
        val users: Array<Rest.User>,
        val vc_muted: Boolean,
        //val video_call_id: Any?,
        //val viewer_id: Double,
        val visual_thread: Any?,
    )

    class DmList(val items: Array<Dm>)

    class ClipShare(val clip: Media)

    class FelixShare(val video: Media, val text: String?)

    class StoryShare(
        val is_linked: Boolean,
        val message: String,
        val reason: Double,
        val text: String,
        val title: String,
    )

    class Reactions(val likes: Array<Any?>, val emojis: Array<Emoji>, val likes_count: Double)

    class Emoji(
        val timestamp: Double,
        val client_context: Double,
        val sender_id: Double,
        val emoji: String,
        val super_react_type: String,
    )
}
