package ir.mahdiparastesh.instatools.json

import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.Versioned
import ir.mahdiparastesh.instatools.view.UiTools

@Suppress("PropertyName", "SpellCheckingInspection", "MemberVisibilityCanBePrivate")
class Dm(
    //val client_context: String,
    val hide_in_thread: Float,
    val is_sent_by_viewer: Boolean,
    //val is_shh_mode: Boolean,
    val item_id: String,
    //val item_type: String,
    //val preview_medias: Array<Any?>,
    val reactions: Reactions?,
    //val show_forward_attribution: Boolean,
    val timestamp: Double,
    //val tq_seq_id: Double,
    //val uq_seq_id: Double,
    val user_id: Double,

    // Item Types
    val action_log: ActionLog?,
    val animated_media: AnimatedMedia?,
    val clip: ClipShare?, // shared some kinda video post
    val direct_media_share: DirectMediaShare?, // tagged you in a post (item_type == "media_share")
    val felix_share: FelixShare?, // shared some kinda video post
    val like: String?, // Amin and Maryam's love: "❤️", not shown anymore though without VPN
    val link: Link?,
    val live_viewer_invite: LiveViewerInvite?,
    val media: Media?, // uploaded a picture or video
    val media_share: Media?, // shared a picture or some kinda video post
    val placeholder: PlaceHolder?,
    val profile: Rest.User?,
    val raven_media: RavenMedia?, // captured and uploaded by the blue button or direct story
    val reel_share: ReelShare?, // the user's own reel which was story once and now is in the archive
    val story_share: StoryShare?, // shared a normal or highlighted story story
    val text: String?, // no different if is a saved reply
    val video_call_event: VideoCallEvent?, // plus audio call
    val voice_media: Voice?,

    // 1. Clip: is a short video which appears in DM as a rectangle and has a video icon in top right
    // plus user profile picture and name at the bottom,
    // clip will not appear in a profile's "Videos" section
    // 2. Felix: is a long videos which appears in DM as a rectangle and has no icons except user
    // profile picture and name at the bottom, this video will appear in their profile's "Videos".
    // 3. Found in {media_share}: There is also some other kind of short video post which appears
    // in DM this time as a SQUARE and also appears in "Videos" section.
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

    /*class ContinuumCursor( // each are either Double or String
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
        fun title() = if (!is_group) users.getOrNull(0)?.visName() else thread_title

        fun exported() = "Exported ${title()}_${UiTools.fileDateTime(Persistent.now())}"
    }

    class ActionLog(
        val bold: Array<Any>,
        val description: String,
        val is_reaction_log: Boolean,
    )

    class AnimatedMedia(
        //val id: String,
        val images: AnimatedMediaImages,
        //val is_random: Boolean,
        //val is_sticker: Boolean, // if chosen from the left list "GIPHY Stickers" not right "GIPHY"
        //val user: Rest.User,
    )

    class AnimatedMediaImages(val fixed_height: AnimatedMediaImage)

    class AnimatedMediaImage(
        val height: String,
        val mp4: String, // .MP4
        val mp4_size: String,
        val size: String,
        val url: String, // .GIF
        val webp: String, // .WEBP
        val webp_size: String,
        val width: String,
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

    class ClipShare(val clip: Media) // TODO: NO TEXT?

    class FelixShare(val video: Media, val text: String?) : PlaceHolder()

    class StoryShare(
        //val is_reel_persisted: Boolean?,
        val media: Media?,
        //val reason: Double?,
        val reel_id: String?,
        val reel_type: String?,
        //val story_share_type: String?,
        val text: String, // the person's message
    ) : PlaceHolder()

    class ReelShare(
        //val is_reel_persisted: Boolean,
        val media: Media?,
        //val reaction_info: ReactionInfo?,
        //val reel_owner_id: Double,
        val reel_type: String,
        val text: String, // the person's message
        //val type: String, // e.g.: "reply"
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
        //val is_shh_mode: Boolean,
        val media: VoiceMedia,
        //val replay_expiring_at_us: Any?,
        //val seen_count: Float,
        //val seen_user_ids: Array<Any>,
        //val view_mode: String,
    )

    class VoiceMedia(
        val audio: Audio,
        //val id: String,
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
        val action: String, // e.g.: "video_call_started" or "video_call_ended"; the same for audio calls
        val call_duration: Double,
        val call_start_time: Double,
        val call_end_time: Double,
        val description: String,
        // e.g.: "fulcrum1378 started a video chat" then "You missed a video chat"
        // e.g.: "You started an audio call" then "Audio call ended"
        val did_join: Boolean?,
        //val encoded_server_data_info: String,
        //val feature_set_str: String,
        //val text_attributes: Array<Any>,
        //val thread_has_audio_only_call: Boolean, // where audio calls and video calls differ
        //val thread_has_drop_in: Boolean,
        //val vc_id: String,
    )

    @Suppress("PropertyName")
    open class PlaceHolder {
        //var is_linked: Boolean? = null // e.g.: false
        var title: String? = null // e.g.: "Post unavailable"
        var message: String? = null // e.g.: "This post is unavailable because it was deleted."
    }

    class RavenMedia(
        //val url_expire_at_secs: Double?,
        //val playback_duration_secs: Float?,
        //val creative_config: Any?,
        //val story_app_attribution: Any?,
        //val create_mode_attribution: Any?,
        //val id: String?,
        //val media_type: Float, // always 1: photo
        original_height: Float?,
        original_width: Float?,
        //val user: Rest.User?,
        //val organic_tracking_token: String,
        image_versions2: Media.ImageVersions2?,
        //val media_id: String?,
    ) : Versioned(image_versions2, original_height, original_width, null, null)

    class LiveViewerInvite(
        //val broadcast: LiveBroadcast?,
        val cta_button_name: String, // e.g.: "Watch Live Video" then "Content Not Available"
        val text: String, // e.g.: ""
    ) : PlaceHolder()

    /*class LiveBroadcast(
        //val id: String,
        //val dash_playback_url: String, // an *.mpd file!!
        //val dash_abr_playback_url: String, // an *.mpd file!!
        //val broadcast_status: String, // e.g.: "interrupted"
        //val viewer_count: Float,
        //val internal_only: Boolean,
        val cover_frame_url: String, // thumbnail
        val cobroadcasters: Array<Any>,
        //val is_player_live_trace_enabled: Float,
        //val is_gaming_content: Boolean,
        //val is_live_comment_mention_enabled: Boolean,
        //val is_live_comment_replies_enabled: Boolean,
        //val is_viewer_comment_allowed: Boolean,
        //val broadcast_owner: Rest.User,
        //val published_time: Double,
        //val hide_from_feed_unit: Boolean,
        //val video_duration: Double,
        //val media_id: String,
        //val live_post_id: String,
        val broadcast_message: String,
        //val organic_tracking_token: String,
        val dimensions: Map<String, Float>, // "width" and "height"
        //val broadcast_experiments: Map<String, Any?>, // much data
        val visibility: Float,
    )*/

    class DirectMediaShare(
        val text: String,
        val media_share_type: String,// e.g.: "tag"
        val tagged_user_id: Double,
        val media: Media,
    )
}
