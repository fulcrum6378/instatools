package ir.mahdiparastesh.instatools.json

import ir.mahdiparastesh.instatools.more.Versioned
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("SpellCheckingInspection", "MemberVisibilityCanBePrivate")
class Media(
    //val can_reply: Boolean?,
    //val can_reshare: Boolean?,
    //val can_see_insights_as_brand: Boolean,
    //val can_send_custom_emojis: Boolean?,
    //val can_view_more_preview_comments: Boolean,
    //val can_viewer_reshare: Boolean,
    //val can_viewer_save: Boolean,
    //val caption: Caption,
    //val caption_is_edited: Boolean,
    val carousel_media: Array<CarouselMedia>?,
    //val carousel_media_count: Double?,
    //val client_cache_key: String,
    val code: String?, // dm uploaded media are nullable
    //val comment_count: Double,
    //val comment_inform_treatment: Map<String, *>,
    //val comment_likes_enabled: Boolean,
    //val comment_threading_enabled: Boolean,
    //val comments: Array<Any>,
    //val commerciality_status: String,
    //val deleted_reason: Float, // 0 => not deleted
    //val device_timestamp: Double, // dm uploaded media are 0.0
    val expiring_at: Double?, // where highlighted stories differ from normal one by nullability but not in DMs
    //val facepile_top_likers: Array<Any>,
    //val featured_products_cta: Any?,
    //val filter_type: Float,
    //val has_audio: Boolean?,
    //val has_liked: Boolean,
    //val has_more_comments: Boolean,
    //val hide_view_all_comment_entrypoint: Boolean,
    val id: String,
    //val igtv_exists_in_viewer_series: Boolean,
    image_versions2: ImageVersions2?,
    //val inline_composer_display_condition: Boolean,
    //val integrity_review_decision: String,
    //val is_dash_eligible: Float,
    //val is_in_profile_grid: Boolean,
    //val is_paid_partnership: Boolean,
    //val is_post_live: Boolean,
    //val is_unified_video: Boolean,
    //val is_visual_reply_commenter_notice_enabled: Boolean,
    //val like_and_view_counts_disabled: Boolean,
    //val like_count: Double,
    //val max_num_visible_preview_comments: Float,
    //val media_cropping_info: Map<String, Any?>,
    val media_type: Float,
    //val music_metadata: MusicMetadata?,
    //val nearly_complete_copyright_match: Boolean,
    //val number_of_qualities: Float,
    //val organic_tracking_token: String,
    original_height: Float?,
    //val original_media_has_visual_reply_media: Boolean,
    original_width: Float?,
    //val photo_of_you: Boolean,
    val pk: String?, // uploaded dm media have no pk but have id
    //val preview_comments: Array<Any>,
    val product_type: String?,
    //val profile_grid_control_enabled: Boolean,
    //val reel_mentions: Array<Map<String, *>?>?,
    //val sharing_friction_info: Map<String, *>,
    //val show_one_tap_fb_share_tooltip: Boolean?,
    //val story_feed_media: Array<Map<String, *>?>?,
    //val story_static_models: Array<Any?>?,
    //val supports_reel_reactions: Boolean?,
    val taken_at: Double, // dm uploaded media are 0.0
    val thumbnails: Thumbnails?,
    val title: String?, // dm uploaded media are nullable
    //val top_likers: Array<Any>,
    val user: Rest.User,
    //val video_codec: String?,
    //val video_dash_manifest: String?,
    //val video_duration: Double?,
    //val video_subtitles_confidence: Double?,
    //val video_subtitles_uri: String?,
    video_versions: Array<VideoVersion>?,
    //val view_count: Double,

    var mahdi_reel_type: String? = null,
    var mahdi_reel_id: String? = null
) : Versioned(
    image_versions2, original_height, original_width, video_versions
) {
    fun thumb() = thumbnails?.sprite_urls?.getOrNull(0)
        ?: carousel_media?.getOrNull(0)?.nearest(WORST)
        ?: nearest(WORST)

    fun thumb(car: CarouselMedia) =
        thumbnails?.sprite_urls?.getOrNull(0) ?: car.nearest(WORST)

    class MediaWrapperApi(
        //var auto_load_more_enabled: Boolean,
        var items: CopyOnWriteArrayList<Media>?,
        var more_available: Boolean,
        //var new_photos: Array<Any?>?,
        var next_max_id: String?,
        //var num_results: Float,
        //var requires_review: Boolean
        //var total_count: Float
    ) // "TAGGED" contains "status", but "POST_ITEM" doesn't.

    class Thumbnails(
        //val video_length: Float,
        //val thumbnail_width: Float,
        //val thumbnail_height: Float,
        //val thumbnail_duration: Float,
        val sprite_urls: Array<String>,
        //val thumbnails_per_row: Float,
        //val total_thumbnail_num_per_sprite: Float,
        //val max_thumbnails_per_sprite: Float,
        //val sprite_width: Float,
        //val sprite_height: Float,
        //val rendered_width: Float,
        //val file_size_kb: Float,
    )

    class CarouselMedia(
        //val can_see_insights_as_brand: Boolean,
        //val carousel_parent_id: String,
        //val comment_inform_treatment: Map<String, *>,
        //val commerciality_status: Boolean,
        //val fb_user_tags: Map<String, Any?>,
        val id: String,
        image_versions2: ImageVersions2,
        val media_type: Float,
        original_height: Float?,
        original_width: Float?,
        val pk: String,
        //val sharing_friction_info: Map<String, *>,
        //val video_codec: String?,
        //val video_dash_manifest: String?,
        //val video_duration: Double?,
        //val video_subtitles_confidence: Double?,
        //val video_subtitles_uri: String?,
        video_versions: Array<VideoVersion>?,
    ) : Versioned(
        image_versions2, original_height, original_width, video_versions
    )

    class ImageVersions2(
        val candidates: Array<Candidate>,
        //val additional_candidates: Map<String, Candidate>
    )

    open class Candidate(val width: Float, val height: Float, val url: String)

    class VideoVersion(
        val type: Float,
        width: Float,
        height: Float,
        url: String,
        val id: String
    ) : Candidate(width, height, url)

    //class Caption : HashMap<String, Any?>()

    /*class MusicMetadata(
        val audio_type: Any?,
        val music_canonical_id: String,
        val music_info: Any?,
        val original_sound_info: Any?,
    )*/
}
