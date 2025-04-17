package ir.mahdiparastesh.instatools.api

import java.net.URLEncoder

@Suppress("KDocUnresolvedReference", "unused")
enum class GraphQlQuery(
    private val doc_id: String,
    private val variables: String,
) {

    /**
     * PolarisSearchBoxRefetchableQuery
     * @param query search query
     * @return [GraphQlData.xdt_api__v1__fbsearch__topsearch_connection]
     */
    SEARCH(
        "9346396502107496",
        "{\"data\":" +
            "{" +
            "\"context\":\"blended\"," +
            "\"include_reel\":\"false\"," + // true
            "\"query\":\"%s\"," +
            "\"rank_token\":\"\"," +
            "\"search_surface\":\"web_top_search\"" +
            "}," +
            "\"hasQuery\":true" +
            "}"
    ),

    /**
     * PolarisStoriesV3TrayContainerQuery
     * @return [GraphQlData.xdt_api__v1__feed__reels_tray]
     */
    FEED_TRAY(
        "8876958245693138",
        "{\"data\":{\"is_following_feed\":false}}"
    ),

    /**
     * PolarisProfilePostsQuery (first fetch)
     * @param username [User.username]
     * @param count default: 12, maximum: 33
     * @return [GraphQlData.xdt_api__v1__feed__user_timeline_graphql_connection]
     */
    PROFILE_POSTS_INITIAL(
        "9457449154339084",
        "{" +
            "\"data\":{" +
            "\"count\":%2\$s," +
            "\"include_reel_media_seen_timestamp\":true," +
            "\"include_relationship_info\":true," +
            "\"latest_besties_reel_media\":true," +
            "\"latest_reel_media\":true" +
            "}," +
            "\"username\":\"%1\$s\"," +
            "\"__relay_internal__pv__PolarisIsLoggedInrelayprovider\":true," +
            "\"__relay_internal__pv__PolarisShareSheetV3relayprovider\":true" +
            "}"
    ),

    /**
     * PolarisProfilePostsTabContentQuery_connection (second and later fetches)
     * @param username [User.username]
     * @param count default: 12, maximum: 33
     * @param after [Media.id] of the last item in the previous fetch
     * @return [GraphQlData.xdt_api__v1__feed__user_timeline_graphql_connection]
     */
    PROFILE_POSTS_MORE(
        "8934560356598281",
        "{" +
            "\"after\":\"%3\$s\"," +
            "\"data\":{\"count\":%2\$s}," +
            "\"username\":\"%1\$s\"," +
            "\"__relay_internal__pv__PolarisIsLoggedInrelayprovider\":true" +
            "}"
    ),

    /**
     * PolarisProfileReelsTabContentQuery (first fetch)
     * @param target_user_id [User.id]
     * @param count default: 12, maximum: 12
     * @return [GraphQlData.xdt_api__v1__clips__user__connection_v2]
     */
    PROFILE_REELS_INITIAL(
        "29938381755760668",
        "{\"data\":{\"include_feed_video\":true,\"page_size\":%2\$s,\"target_user_id\":\"%1\$s\"}}"
    ),

    /**
     * PolarisProfileReelsTabContentQuery_connection (second and later fetches)
     * @param target_user_id [User.id]
     * @param count default: 12, maximum: 12
     * @param after a non-replicable hashed cursor
     * @return [GraphQlData.xdt_api__v1__clips__user__connection_v2]
     */
    PROFILE_REELS_MORE(
        "8931245513664134",
        "{" +
            "\"after\":\"%3\$s\"," +
            "\"before\":null," +
            "\"data\":" +
            "{" +
            "\"include_feed_video\":true," +
            "\"page_size\":%2\$s," +
            "\"target_user_id\":\"%1\$s\"" +
            "}," +
            "\"first\":4," +
            "\"last\":null" +
            "}"
    ),

    /**
     * PolarisProfileTaggedTabContentQuery (first fetch)
     * @param user_id [User.id]
     * @param count default: 12, maximum: 12
     * @return [GraphQlData.xdt_api__v1__usertags__user_id__feed_connection]
     */
    PROFILE_TAGGED_INITIAL(
        "8626574937464773",
        "{\"count\":%2\$s,\"user_id\":\"%1\$s\"}"
    ),

    /**
     * PolarisProfileTaggedTabContentQuery_connection (second and later fetches)
     * @param user_id [User.id]
     * @param count default: 12, maximum: 21
     * @param after [Media.id] of the last item in the previous fetch
     * @return [GraphQlData.xdt_api__v1__usertags__user_id__feed_connection]
     */
    PROFILE_TAGGED_MORE(
        "8786107121469577",
        "{" +
            "\"after\":\"%3\$s\"," +
            "\"before\":null," +
            "\"count\":%2\$s," +
            "\"first\":12," +
            // `first` might not be identical to `count`; because in the original `PROFILE_REELS_MORE`,
            // `first` is "4" while `count` is "12"!
            "\"last\":null," +
            "\"user_id\":\"%1\$s\"" +
            "}"
    ),

    /**
     * PolarisPostRootQuery
     * @param shortcode
     * @return [GraphQlData.xdt_api__v1__media__shortcode__web_info]
     */
    POST_ROOT(
        "18086740648321782",
        "{\"shortcode\":\"%s\"}"
    ),

    /**
     * PolarisStoriesV3ReelPageStandaloneQuery
     * @param user_id `"\"[User.id]\""` separated by `,`
     * @return [GraphQlData.xdt_api__v1__feed__reels_media]
     */
    STORY(
        "27760393576942150",
        "{\"reel_ids_arr\":[%s]}"
    ),

    /**
     * PolarisProfileStoryHighlightsTrayContentQuery
     * @param user_id [User.id]
     * @return [GraphQlData.highlights]
     */
    PROFILE_HIGHLIGHTS_TRAY(
        "8198469583554901",
        "{\"user_id\":\"%s\"}"
    ),

    /**
     * PolarisStoriesV3HighlightsPageQuery
     * @param reel_ids `\"[Story.id]\"`
     * @param initial_reel_id `\"[Story.id]\"` separated by `,`
     * @return [GraphQlData.xdt_api__v1__feed__reels_media__connection]
     */
    HIGHLIGHTS(
        "29001692012763642",
        "{" +
            "\"initial_reel_id\":%2\$s," +
            "\"reel_ids\":[%1\$s]," +
            "\"first\":3," +
            "\"last\":2" +
            "}"
    ),

    /**
     * usePolarisToggleFollowUserFollowMutation
     * @param target_user_id [User.id]
     * @return [GraphQlData.xdt_create_friendship]
     */
    FOLLOW(
        "8681003828679375",
        "{\"target_user_id\":\"%s\"}"
    ),

    /**
     * usePolarisToggleFollowUserUnfollowMutation
     * @param target_user_id [User.id]
     * @return [GraphQlData.xdt_destroy_friendship]
     */
    UNFOLLOW(
        "8965103070189304",
        "{\"target_user_id\":\"%s\"}"
    ),

    /**
     * usePolarisSetBestiesMutation
     * @param add `\"[User.id]\"` separated by `,`
     * @param remove `\"[User.id]\"` separated by `,`
     * @return [GraphQlData.xdt_set_besties]
     */
    BESTIES(
        "7489805084467496",
        "{\"add\":[%1\$s],\"remove\":[%2\$s],\"source\":\"profile\"}"
    ),

    /**
     * usePolarisUpdateFeedFavoritesMutation
     * @param add `\"[User.id]\"` separated by `,`
     * @param remove `\"[User.id]\"` separated by `,`
     * @return [GraphQlData.xdt_update_feed_favorites]
     */
    FAVORITE(
        "25141617315482520",
        "{\"add\":[%1\$s],\"remove\":[%2\$s],\"source\":\"profile\"}"
    ),

    /**
     * usePolarisMutePostsMutation
     * @param target_posts_author_id [User.id]
     * @return [GraphQlData.xdt_user_mute_posts]
     */
    MUTE_POSTS(
        "7845855428811431",
        "{\"target_posts_author_id\":\"%s\"}"
    ),

    /**
     * usePolarisUnmutePostsMutation
     * @param target_posts_author_id [User.id]
     * @return [GraphQlData.xdt_user_unmute_posts]
     */
    UNMUTE_POSTS(
        "7752090331521095",
        "{\"target_posts_author_id\":\"%s\"}"
    ),

    /**
     * usePolarisMuteStoryMutation
     * @param target_reel_author_id [User.id]
     * @return [GraphQlData.xdt_user_mute_story]
     */
    MUTE_STORY(
        "7811910972202346",
        "{\"target_reel_author_id\":\"%s\"}"
    ),

    /**
     * usePolarisUnmuteStoryMutation
     * @param target_reel_author_id [User.id]
     * @return [GraphQlData.xdt_user_unmute_story]
     */
    UNMUTE_STORY(
        "7696114017140185",
        "{\"target_reel_author_id\":\"%s\"}"
    ),

    /**
     * usePolarisRestrictMutation
     * @param target_user_ids `\"[User.id]\"` separated by `,`
     * @return [GraphQlData.xdt_api__v1__restrict_action__restrict_many]
     */
    RESTRICT(
        "7456259841095672",
        "{\"target_user_ids\":[%s]}"
    ),

    /**
     * usePolarisUnrestrictMutation
     * @param [User.id]
     * @return [GraphQlData.xdt_api__v1__restrict_action__unrestrict]
     */
    UNRESTRICT(
        "7189308067834241",
        "{\"target_user_id\":\"%s\"}"
    ),

    /**
     * usePolarisBlockManyMutation
     * @param target_user_ids `\"[User.id]\"` separated by `,`
     * @return [GraphQlData.xdt_block_many]
     */
    BLOCK(
        "7582138121880080",
        "{\"target_user_ids\":[%s]}"
    ),

    /**
     * usePolarisUnblockMutation
     * @param target_user_id [User.id]
     * @return [GraphQlData.xdt_unblock]
     */
    UNBLOCK(
        "7978259088859181",
        "{\"target_user_id\":\"%s\"}"
    ),

    /**
     * usePolarisLikeMediaLikeMutation
     * @param media_id [Media.id]
     * @return [GraphQlData.xdt_api__v1__media__media_id__like]
     */
    LIKE_POST(
        "8552604541488484",
        "{\"media_id\":\"%s\"}"
    ),

    /**
     * usePolarisLikeMediaUnlikeMutation
     * @param media_id [Media.id]
     * @return [GraphQlData.xdt_api__v1__media__media_id__unlike]
     */
    UNLIKE_POST(
        "8525474704176507",
        "{\"media_id\":\"%s\"}"
    ),

    /**
     * usePolarisStoriesV3LikeMutationLikeMutation
     * @param mediaId [Media.id]
     * @return [GraphQlData.xdt_api__v1__story_interactions__send_story_like]
     *
     * Applicable for both daily and highlighted stories.
     * BEWARE that it's `mediaId` not 'media_id'!!
     */
    LIKE_STORY(
        "7324313080956832",
        "{\"mediaId\":\"%s\"}"
    ),

    /**
     * usePolarisStoriesV3LikeMutationUnlikeMutation
     * @param mediaId [Media.id]
     * @return [GraphQlData.xdt_api__v1__story_interactions__unsend_story_like]
     *
     * Applicable for both daily and highlighted stories.
     * BEWARE that it's `mediaId` not 'media_id'!!
     */
    UNLIKE_STORY(
        "6826730164093779",
        "{\"mediaId\":\"%s\"}"
    ),

    /**
     * usePolarisSaveMediaSaveMutation
     * @param media_id [Media.id]
     * @return [GraphQlData.xdt_api__v1__web__save__media_id__save]
     */
    SAVE(
        "7658071600908962",
        "{\"media_id\":\"%s\"}"
    ),

    /**
     * usePolarisSaveMediaUnsaveMutation
     * @param media_id [Media.id]
     * @return [GraphQlData.xdt_api__v1__web__save__media_id__unsave]
     */
    UNSAVE(
        "8122123554479056",
        "{\"media_id\":\"%s\"}"
    );

    fun body(vararg params: String) =
        "doc_id=$doc_id&variables=${URLEncoder.encode(variables.format(*params), "utf-8")}"
}
