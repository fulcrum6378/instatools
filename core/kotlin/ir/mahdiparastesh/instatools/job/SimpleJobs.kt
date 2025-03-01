package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.api.*

object SimpleJobs {

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
        return Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true, GraphQlQuery.POST_ROOT.body(shortcode)
        ).data!!.xdt_api__v1__media__shortcode__web_info!!.items[0]
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
        val gql = Api.json<GraphQl>(Api.Endpoint.QUERY.url, true, graphQlQuery.body(med.id()))
        result(gql.data != null)
    }
}
