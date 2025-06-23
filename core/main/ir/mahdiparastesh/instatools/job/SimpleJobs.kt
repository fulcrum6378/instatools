package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.api.User
import ir.mahdiparastesh.instatools.util.Utils
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

object SimpleJobs {

    /** @return same as [User] received from [Api.Endpoint.PROFILE_INFO] */
    fun userFromHtml(html: String): User? {

        // extract JSON from HTML
        var read = html
        val starter = "{\"require\":[[\"ScheduledServerJS\""
        var json: String? = null
        while (read.contains(starter)) {
            read = read.substring(read.indexOf(starter))
            json = read.substringBefore("</script>")
            if (json.contains("XIGSharedData")) break
            // we don't need XIGSharedData itself; we just need the JSON which uniquely contains it.
            json = null
            read = read.substringAfter("</script>")
        }
        if (json == null) return null

        // extract [User] from JSON
        return try {
            val scheduledServerJS =
                ((Json.decodeFromString<JsonObject>(json)["require"] as JsonArray)[0] as JsonArray)[3]
                    as JsonArray // only the first element of `scheduledServerJS` is useful.
            val define = ((scheduledServerJS[0] as JsonObject)["__bbox"] as JsonObject)["define"]
                as JsonArray // everything is in `define`, it contains ~300 elements!
            var head: JsonPrimitive
            val polarisViewer = (define.find {
                head = (it as JsonArray)[0] as JsonPrimitive
                head.isString && head.content == "PolarisViewer"
            } as JsonArray)[2] as JsonObject

            val polarisData = polarisViewer["data"]
            if (polarisData is JsonObject)
                Api.json.decodeFromJsonElement<User>(polarisData)
            else  // when the user switches between pages rapidly
                null  // kotlinx.serialization.json.JsonNull
        } catch (_: SerializationException) {
            null
        }
    }

    /**
     * Resolves download URLs of desired posts or reels via their official links.
     * @throws Api.FailureException
     */
    @Throws(Api.FailureException::class)
    fun handlePostLink(link: String): Media {
        /*if ("instagram://media?id=" in html) {
            val medId = html.substringAfter("instagram://media?id=").substringBefore("\"")
            if (System.getenv("debug") == "1")
                println("Media ID: $medId")
            val singleItemList =
                Api.json<Rest.LazyList<Media>>(Api.Endpoint.MEDIA_INFO.url.format(medId))
            downloadTask.download(singleItemList.items.first(), idealSize, link)
        }*/
        val shortcode = when {
            "/p/" in link -> link.substringAfter("/p/").substringBefore("/")
            "/reel/" in link -> link.substringAfter("/reel/").substringBefore("/")
            else -> throw IllegalArgumentException("Links must be either for a post or a reel!")
        }
        return Api.graphQl(GraphQlQuery.POST_ROOT.body(shortcode))
            .data!!.xdt_api__v1__media__shortcode__web_info!!.items[0]
    }

    /**
     * If a user doesn't exist, HTTP error code 404 will be thrown!
     * @throws Api.FailureException
     */
    @Throws(Api.FailureException::class)
    fun userInfo(userId: String): User =
        Api.json<Rest.UserInfo>(Api.Endpoint.USER_INFO.url.format(userId)).user

    /**
     * If a user doesn't exist, HTTP error code 404 will be thrown!
     * @throws Api.FailureException
     */
    @Throws(Api.FailureException::class)
    fun profileInfo(userName: String): User =
        Api.json<GraphQl>(Api.Endpoint.PROFILE_INFO.url.format(userName)).data!!.user!!

    /**
     * Performs any of the actions specified in [GraphQlQuery] concerning [Media].
     * @throws Api.FailureException
     */
    @Throws(Api.FailureException::class)
    fun actionMedia(
        med: Media, graphQlQuery: GraphQlQuery, result: (success: Boolean) -> Unit
    ) {
        val gql = Api.graphQl(graphQlQuery.body(med.id()))
        result(gql.data != null)
    }

    @Throws(Api.FailureException::class)
    fun markStoryAsSeen(story: Story, item: Int) {
        val media = story.items!![item]
        Api.graphQl(
            GraphQlQuery.STORY_SEEN.body(
                story.id,
                media.id(),
                story.user.id(),
                media.taken_at!!.toString(),
                Utils.now().toString()
            )
        )
    }
}
