package ir.mahdiparastesh.instatools.api

import ir.mahdiparastesh.instatools.api.GraphQl.Page
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")
data class GraphQl(
    val data: GraphQlData?,
    //val errors: List<Map<String, Any>>?,
    //val extensions: Map<String, Any>,
    override val status: String // can be "ok" with HTTP code 200 while having errors!!!
) : Rest {

    class GraphQlData(
        val highlights: Page<Story>?,
        val user: User?,
        val xdt_api__v1__feed__user_timeline_graphql_connection: Page<Media>?, // profile posts
        val xdt_api__v1__feed__reels_media: Story.Wrapper?, // daily stories
        val xdt_api__v1__feed__reels_media__connection: Page<Story>?, // highlighted stories
        //val xdt_api__v1__media__media_id__like: Liked?, // liked a post
        //val xdt_api__v1__media__shortcode__web_info: MediaShortcodeWebInfo?,
        //val xdt_api__v1__story_interactions__send_story_like: Liked?, // liked a story
        //val xdt_api__v1__story_interactions__unsend_story_like: Liked?, // unliked a story
        val xdt_api__v1__usertags__user_id__feed_connection: Page<Media>?, // tagged posts
    )

    data class Page<Node>(
        val edges: CopyOnWriteArrayList<Edge<Node>>,
        val page_info: PageInfo,
    )

    data class Edge<Node>(val node: Node)

    data class PageInfo(
        //val end_cursor: String,
        var has_next_page: Boolean,
        //val has_previous_page: Boolean,
        //val start_cursor: String?,
    )

    //class MediaShortcodeWebInfo(val items: List<Media>)

    //data class Liked(val __typename: String) // "XDTEmptyRecord"
}
