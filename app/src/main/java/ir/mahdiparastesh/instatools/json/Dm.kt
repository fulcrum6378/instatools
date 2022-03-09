package ir.mahdiparastesh.instatools.json

@Suppress("PropertyName", "SpellCheckingInspection")
class Dm(
    //val client_context: String,
    val hide_in_thread: Float,
    val is_sent_by_viewer: Boolean,
    //val is_shh_mode: Boolean,
    val item_id: String,
    val item_type: String,
    val preview_medias: Array<Any?>,
    val reactions: Reactions?,
    //val show_forward_attribution: Boolean,
    val timestamp: Double,
    //val tq_seq_id: Double,
    //val uq_seq_id: Double,
    //val user_id: Double,

    // Item Types
    val action_log: ActionLog?,
    val animated_media: AnimatedMedia?,
    val clip: ClipShare?,
    val felix_share: FelixShare?,
    val like: String?,
    val link: Link?,
    val live_viewer_invite: Any?,
    val media: Media?,
    val media_share: Media?,
    val placeholder: PlaceHolder?,
    val profile: Rest.User?,
    val raven_media: Any?,
    val reel_share: ReelShare?,
    val story_share: StoryShare?,
    val text: String?,
    val video_call_event: VideoCallEvent?,
    val voice_media: Voice?,
) {
    class Inbox(
        //val blended_inbox_enabled: Boolean,
        var has_older: Boolean,
        //val next_cursor: ContinuumCursor?,
        var oldest_cursor: String?,
        //val prev_cursor: ContinuumCursor?,
        var threads: ArrayList<DmThread>,
        //val unseen_count: Double,
        //val unseen_count_ts: Double,
    )

    /*class ContinuumCursor(// each are either Double or String
        val cursor_timestamp_seconds: Any,
        val cursor_relevancy_score: Any,
        val cursor_thread_v2_id: Any,
    )*/

    class DmThread(
        //val admin_user_ids: Array<Any?>,
        //val approval_required_for_new_members: Boolean,
        //val archived: Boolean,
        //val assigned_admin_id: Double,
        //val bc_partnership: Boolean,
        //val business_thread_folder: Double,
        //val canonical: Boolean,
        //val encoded_server_data_info: String,
        //val folder: Double,
        //val group_link_joinable_mode: Double,
        //val has_groups_xac_ineligible_user: Boolean,
        //val has_newer: Boolean,
        var has_older: Boolean,
        //val input_mode: Double,
        //val inviter: Rest.User,
        //val is_close_friend_thread: Boolean,
        //val is_fanclub_subscriber_thread: Boolean,
        val is_group: Boolean,
        //val is_translation_enabled: Boolean,
        //val is_xac_thread: Boolean,
        val items: ArrayList<Dm>,
        //val joinable_group_link: String,
        val last_activity_at: Double,
        //val last_non_sender_item_at: Double,
        //val last_permanent_item: Dm,
        //val last_seen_at: Map<String, Map<String, Any?>>,
        //val left_users: Array<Any>,
        //val marked_as_unread: Boolean,
        //val mentions_muted: Boolean,
        //val muted: Boolean,
        //val named: Boolean,
        //val newest_cursor: String,
        //val next_cursor: String,
        //val oldest_cursor: String,
        //val pending: Boolean,
        //val pending_user_ids: Array<Any?>,
        //val prev_cursor: String,
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
        //val thread_image: Any?,
        //val thread_label: Double,
        //val thread_languages: Map<String, String>,
        val thread_title: String,
        //val thread_type: String,
        //val thread_v2_id: String,
        //val translation_banner_impression_count: Double,
        val users: Array<Rest.User>,
        //val vc_muted: Boolean,
        //val video_call_id: Any?,
        //val viewer_id: Double,
        //val visual_thread: Any?,
    ) {
        fun firstUser(): Rest.User? = users.getOrNull(0)
    }

    class DmList(val items: Array<Dm>)


    class ActionLog(
        val bold: Array<Any>,
        val description: String,
        val is_reaction_log: Boolean,
    )

    class AnimatedMedia(
        val images: Any?
    )

    class Link(
        //val client_context: String,
        val link_context: LinkContext,
        //val mutation_token: String,
        val text: String,
    )

    class LinkContext(
        val link_url: String,
        //val link_title: String,
        //val link_summary: String,
        //val link_image_url: String,
    )

    class ClipShare(val clip: Media)

    class FelixShare(val video: Media, val text: String) : PlaceHolder()

    class StoryShare(
        //val is_reel_persisted: Boolean?,
        val media: Media?,
        //val reason: Double?,
        //val reel_id: String?,
        //val reel_type: String?,
        //val story_share_type: String?,
        val text: String,
    ) : PlaceHolder()

    class ReelShare(
        //val is_reel_persisted: Boolean,
        val media: Media?,
        //val reaction_info: ReactionInfo?,
        //val reel_owner_id: Double,
        //val reel_type: String,
        val text: String,
        val type: String,
    ) : PlaceHolder()

    class Reactions(
        //val likes: Array<Any?>,
        val emojis: Array<Emoji>,
        //val likes_count: Double
    )

    class Emoji(
        //val timestamp: Double,
        //val client_context: String,
        //val sender_id: Double,
        val emoji: String,
        //val super_react_type: String,
    )

    class ReactionInfo(
        val emoji: String,
        val intensity: Any?
    )

    class Voice(
        val is_shh_mode: Boolean,
        val replay_expiring_at_us: Any?,
        val seen_count: Float,
        val seen_user_ids: Array<Any>,
        val view_mode: String,
    )

    class VoiceMedia(
        val audio: Audio,
        val id: String,
        //val media_type: Float,
        //val organic_tracking_token: String,
        //val product_type: String,
        //val user: Rest.User,
    )

    open class AudioSrc {
        lateinit var audio_src: String
    }

    class Audio(
        //val audio_src_expiration_timestamp_us: Double,
        val duration: Double,
        val fallback: AudioSrc,
        val waveform_data: Array<Float>, // waves
        //val waveform_sampling_frequency_hz: Float,
    ) : AudioSrc()

    class VideoCallEvent(
        val action: String,
        val call_duration: Double,
        val call_start_time: Double,
        val call_end_time: Double,
        val description: String,
        val did_join: Boolean,
        //val encoded_server_data_info: String,
        //val feature_set_str: String,
        //val text_attributes: Array<Any>,
        //val thread_has_audio_only_call: Boolean,
        //val thread_has_drop_in: Boolean,
        //val vc_id: String,
    )

    @Suppress("PropertyName")
    open class PlaceHolder {
        var is_linked: Boolean? = null
        var title: String? = null
        var message: String? = null
    }
}
