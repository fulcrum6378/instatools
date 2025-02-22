package ir.mahdiparastesh.instatools.api

import java.net.URLEncoder

@Suppress("KDocUnresolvedReference", "unused")
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
     * @param after [Media.id] of the last item in the previous fetch
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
     * @param reel_ids `\"[Story.id]\"`
     * @param initial_reel_id `\"[Story.id]\"` separated by `,`
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
        "{\"target_user_id\":\"%s\"}"
    ),

    /**
     * usePolarisToggleFollowUserUnfollowMutation
     * @param target_user_id [User.id]
     */
    UNFOLLOW(
        "8965103070189304",
        "{\"target_user_id\":\"%s\"}"
    ),

    /**
     * usePolarisSetBestiesMutation
     * @param add `\"[User.id]\"` separated by `,`
     * @param remove `\"[User.id]\"` separated by `,`
     */
    BESTIES(
        "7489805084467496",
        "{\"add\":[%1\$s],\"remove\":[%2\$s],\"source\":\"profile\"}"
    ),

    /**
     * usePolarisUpdateFeedFavoritesMutation
     * @param add `\"[User.id]\"` separated by `,`
     * @param remove `\"[User.id]\"` separated by `,`
     */
    FAVORITE(
        "25141617315482520",
        "{\"add\":[%1\$s],\"remove\":[%2\$s],\"source\":\"profile\"}"
    ),

    /**
     * usePolarisMutePostsMutation
     * @param target_posts_author_id [User.id]
     */
    MUTE_POSTS(
        "7845855428811431",
        "{\"target_posts_author_id\":\"%s\"}"
    ),

    /**
     * usePolarisUnmutePostsMutation
     * @param target_posts_author_id [User.id]
     */
    UNMUTE_POSTS(
        "7752090331521095",
        "{\"target_posts_author_id\":\"%s\"}"
    ),

    /**
     * usePolarisMuteStoryMutation
     * @param target_reel_author_id [User.id]
     */
    MUTE_STORY(
        "7811910972202346",
        "{\"target_reel_author_id\":\"%s\"}"
    ),

    /**
     * usePolarisUnmuteStoryMutation
     * @param target_reel_author_id [User.id]
     */
    UNMUTE_STORY(
        "7696114017140185",
        "{\"target_reel_author_id\":\"%s\"}"
    ),

    /**
     * usePolarisRestrictMutation
     * @param target_user_ids `\"[User.id]\"` separated by `,`
     */
    RESTRICT(
        "7456259841095672",
        "{\"target_user_ids\":[%s]}"
    ),

    /**
     * usePolarisUnrestrictMutation
     * @param [User.id]
     */
    UNRESTRICT(
        "7189308067834241",
        "{\"target_user_id\":\"%s\"}"
    ),

    /**
     * usePolarisBlockManyMutation
     * @param target_user_ids `\"[User.id]\"` separated by `,`
     */
    BLOCK(
        "7582138121880080",
        "{\"target_user_ids\":[%s]}"
    ),

    /**
     * usePolarisUnblockMutation
     * @param target_user_id [User.id]
     */
    UNBLOCK(
        "7978259088859181",
        "{\"target_user_id\":\"%s\"}"
    ),

    /**
     * usePolarisLikeMediaLikeMutation
     * @param media_id [Media.id]
     */
    LIKE_POST(
        "8552604541488484",
        "{\"media_id\":\"%s\"}"
    ),

    /**
     * usePolarisLikeMediaUnlikeMutation
     * @param media_id [Media.id]
     */
    UNLIKE_POST(
        "8525474704176507",
        "{\"media_id\":\"%s\"}"
    ),

    /**
     * usePolarisStoriesV3LikeMutationLikeMutation
     * @param mediaId [Media.id]
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
     */
    SAVE(
        "7658071600908962",
        "{\"media_id\":\"%s\"}"
    ),

    /**
     * usePolarisSaveMediaUnsaveMutation
     * @param media_id [Media.id]
     */
    UNSAVE(
        "8122123554479056",
        "{\"media_id\":\"%s\"}"
    );

    fun body(vararg params: String) =
        "doc_id=$doc_id&variables=${URLEncoder.encode(variables.format(*params), "utf-8")}"
}
