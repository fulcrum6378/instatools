package ir.mahdiparastesh.instatools.api

import java.net.URLEncoder

@Suppress(
    "PrivatePropertyName", "SpellCheckingInspection", "KDocUnresolvedReference", "unused"
)
enum class GraphQlQuery(
    private val doc_id: String,
    private val variables: String,
) {
    /**
     * PolarisProfilePostsQuery
     * @param username [User.username]
     * @param count default: 12, maximum: 33
     * @param after [Media.id] of the last item in the previous fetch
     */
    PROFILE_POSTS(
        "8934560356598281",
        "{" +
            "\"after\":\"%3\$s\"," +
            "\"data\":{\"count\":%2\$s}," +
            "\"username\":\"%1\$s\"," +
            "\"__relay_internal__pv__PolarisIsLoggedInrelayprovider\":true" +
            "}"
    ),

    /**
     * PolarisProfileTaggedTabContentQuery (first fetch)
     * @param user_id [User.id]
     * @param count default: 12
     */
    PROFILE_TAGGED(
        "8626574937464773",
        "{\"count\":%2\$s,\"user_id\":\"%1\$s\"}"
    ),

    /**
     * PolarisProfileTaggedTabContentQuery (second and later fetches)
     * @param user_id [User.id]
     * @param count default: 12
     * @param after [Media.pk] of the last item in the previous fetch
     */
    PROFILE_TAGGED_CURSORED(
        "8786107121469577",
        "{\"after\":\"%3\$s\",\"first\":12,\"count\":%2\$s,\"user_id\":\"%1\$s\"}"
    ),

    /**
     * PolarisPostRootQuery
     * @param shortcode
     */
    POST_ROOT(
        "18086740648321782",
        "{\"shortcode\":\"%s\"}"
    ),

    /**
     * PolarisStoriesV3ReelPageStandaloneQuery
     * @param user_id `"\"[User.id]\""` separated by `,`
     */
    STORY(
        "27760393576942150",
        "{\"reel_ids_arr\":[%s]}"
    ),

    /**
     * PolarisProfileStoryHighlightsTrayContentQuery
     * @param user_id [User.id]
     */
    PROFILE_HIGHLIGHTS_TRAY(
        "8198469583554901",
        "{\"user_id\":\"%s\"}"
    ),

    /**
     * PolarisStoriesV3HighlightsPageQuery
     * @param reel_ids `"\"[Story.id]\""`
     * @param initial_reel_id `"\"[Story.id]\""` separated by `,`
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
     */
    FOLLOW(
        "8681003828679375",
        "{\"target_user_id\":\"22716405900\"}"
    ),

    /**
     * usePolarisToggleFollowUserUnfollowMutation
     * @param target_user_id [User.id]
     */
    UNFOLLOW(
        "8965103070189304",
        "{\"target_user_id\":\"22716405900\"}"
    ),

    /**
     * usePolarisLikeMediaLikeMutation
     * @param media_id [Media.pk]
     */
    LIKE_POST(
        "8552604541488484",
        "{\"media_id\":\"%s\"}"
    ),

    /**
     * usePolarisLikeMediaUnlikeMutation
     * @param media_id [Media.pk]
     */
    UNLIKE_POST(
        "8525474704176507",
        "{\"media_id\":\"%s\"}"
    ),

    /**
     * usePolarisStoriesV3LikeMutationLikeMutation
     * @param media_id [Media.pk]
     *
     * Applicable for both daily and highlighted stories.
     */
    LIKE_STORY(
        "7324313080956832",
        "{\"mediaId\":\"%s\"}"
    ),

    /**
     * usePolarisStoriesV3LikeMutationUnlikeMutation
     * @param media_id [Media.pk]
     *
     * Applicable for both daily and highlighted stories.
     */
    UNLIKE_STORY(
        "6826730164093779",
        "{\"mediaId\":\"%s\"}"
    ),

    /**
     * usePolarisSaveMediaSaveMutation
     * @param media_id [Media.pk]
     */
    SAVE(
        "7658071600908962",
        "{\"mediaId\":\"%s\"}"
    ),

    /**
     * usePolarisSaveMediaUnsaveMutation
     * @param media_id [Media.pk]
     */
    UNSAVE(
        "8122123554479056",
        "{\"mediaId\":\"%s\"}"
    );

    fun body(vararg params: String) =
        "doc_id=$doc_id&variables=${URLEncoder.encode(variables.format(*params), "utf-8")}"
}
