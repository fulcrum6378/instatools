package ir.mahdiparastesh.instatools.api

@Suppress("PropertyName", "SpellCheckingInspection")
class GraphQl(
    val data: GraphQlData?,
    //val errors: Array<GraphQlError>?,
    //val extensions: Map<String, Any>,
    override val status: String // can be "ok" with HTTP code 200 while having errors!!!
) : Rest {

    class GraphQlData(
        val highlights: Page<Story>?,
        val user: User?,
        val xdt_api__v1__feed__reels_media: Story.Wrapper?, // daily stories
        val xdt_api__v1__feed__reels_media__connection: Page<Story>?, // highlighted stories
        val xdt_api__v1__feed__user_timeline_graphql_connection: Page<Media>?, // profile posts
        //val xdt_api__v1__media__media_id__like: MediaInteraction?, // like post
        //val xdt_api__v1__media__media_id__unlike: MediaInteraction?, // unlike post
        //val xdt_api__v1__media__shortcode__web_info: MediaShortcodeWebInfo?, // media info
        //val xdt_api__v1__restrict_action__restrict_many: Array<UserInteraction>?, // restrict
        //val xdt_api__v1__restrict_action__unrestrict: UserInteraction?, // unrestrict
        //val xdt_api__v1__story_interactions__send_story_like: MediaInteraction?, // like story
        //val xdt_api__v1__story_interactions__unsend_story_like: MediaInteraction?, // unlike story
        val xdt_api__v1__usertags__user_id__feed_connection: Page<Media>?, // tagged posts
        //val xdt_api__v1__web__save__media_id__save: MediaInteraction?, // save
        //val xdt_api__v1__web__save__media_id__unsave: MediaInteraction?, // unsave
        //val xdt_block_many: Array<UserInteraction>?, // block
        //val xdt_create_friendship: UserInteraction?, // follow
        //val xdt_destroy_friendship: UserInteraction?, // unfollow
        //val xdt_set_besties: Array<UserInteraction>?, // close friends
        //val xdt_unblock: UserInteraction?, // unblock
        //val xdt_update_feed_favorites: Array<UserInteraction>?, // favourites
        //val xdt_user_mute_posts: UserInteractionWrapper?, // mute posts
        //val xdt_user_mute_story: UserInteractionWrapper?, // mute story
        //val xdt_user_unmute_posts: UserInteractionWrapper?, // unmute posts
        //val xdt_user_unmute_story: UserInteractionWrapper?, // unmute story
    )

    /*class GraphQlError(
        //val message: String,
        //val code: Int?,
        //val summary: String?,
        //val description: String?,
        //val path: Array<String>,
        //val extensions: Map<String, String>?,
        //val severity: String
    )*/

    class Page<Node>(
        val edges: List<Edge<Node>>,
        val page_info: PageInfo,
    )

    class Edge<Node>(val node: Node)

    class PageInfo(
        //val end_cursor: String,
        val has_next_page: Boolean,
        //val has_previous_page: Boolean,
        //val start_cursor: String?,
    )

    //class MediaShortcodeWebInfo(val items: List<Media>)

    /*class UserInteractionWrapper(
        val muted_user: UserInteraction?, // posts/story
        val unmuted_user: UserInteraction?, // posts/story
    )*/

    /*class UserInteraction(
        //val __typename: String?, // only in block/unblock, always equals "XDTUserDict"
        //val pk: String?, // only in restrict/unrestrict
        //val username: String?, // only in follow/unfollow
        val friendship_status: FriendshipStatus?, // missing only in block/unblock
        //val id: String
    )*/

    //class MediaInteraction(val __typename: String) // always "XDTEmptyRecord"
}
