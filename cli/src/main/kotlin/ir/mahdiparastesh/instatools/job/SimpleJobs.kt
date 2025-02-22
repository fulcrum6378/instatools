package ir.mahdiparastesh.instatools.job

import com.google.gson.Gson
import ir.mahdiparastesh.instatools.Context.api
import ir.mahdiparastesh.instatools.Context.downloader
import ir.mahdiparastesh.instatools.api.*

object SimpleJobs {

    /** Resolves download URLs of desired posts or reels via their official links. */
    fun handlePostLink(link: String, idealSize: Float) {
        val html = api.page(link)
        val data = RelayPrefetchedStreamCache.crawl(html) { // hashMapOf<String, Map<String, Any>>()
            it.contains("PolarisPostRootQueryRelayPreloader")
        }
        if (System.getenv("debug") == "1")
            println("RelayPrefetchedStreamCache: " + data.keys.joinToString(", "))

        if ("PolarisPostRootQueryRelayPreloader" in data) {
            @Suppress("UNCHECKED_CAST")
            val medMap = (data["PolarisPostRootQueryRelayPreloader"]!!["items"] as List<Map<String, Any>>)[0]
            downloader.download(Gson().fromJson(Gson().toJson(medMap), Media::class.java), idealSize, link)
        } else if ("instagram://media?id=" in html) {
            val medId = html.substringAfter("instagram://media?id=").substringBefore("\"")
            if (System.getenv("debug") == "1")
                println("Media ID: $medId")
            val singleItemList = api.call<Rest.LazyList<Media>>(
                Api.Endpoint.MEDIA_INFO.url.format(medId),
                Rest.LazyList::class, generics = arrayOf(Media::class),
            )
            downloader.download(singleItemList.items.first(), idealSize, link)
        } else
            if (System.getenv("debug") == "1")
                System.err.println("Shall we re-implement PageConfig?")
    }

    /** If a user doesn't exist, HTTP error code 404 will be thrown! */
    fun userInfo(userId: String): User =
        api.call<Rest.UserInfo>(Api.Endpoint.USER_INFO.url.format(userId), Rest.UserInfo::class).user

    /** If a user doesn't exist, HTTP error code 404 will be thrown! */
    fun profileInfo(userName: String): User =
        api.call<GraphQl>(Api.Endpoint.PROFILE_INFO.url.format(userName), GraphQl::class).data!!.user!!

    /** Performs a GraphQL action on a post/reel/story. */
    fun actionMedia(
        med: Media, graphQlQuery: GraphQlQuery, result: (success: Boolean) -> Unit
    ) {
        val gql = api.call<GraphQl>(
            Api.Endpoint.QUERY.url, GraphQl::class, true, graphQlQuery.body(med.pk())
        )
        result(gql.data != null)
    }
}
