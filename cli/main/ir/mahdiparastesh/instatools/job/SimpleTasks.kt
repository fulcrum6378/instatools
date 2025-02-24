package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.Context.downloadTask
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.RelayPrefetchedStreamCache
import ir.mahdiparastesh.instatools.api.Rest
import kotlin.collections.contains

object SimpleTasks {

    /**
     * Resolves download URLs of desired posts or reels via their official links.
     * @throws Api.FailureException
     */
    @Throws(Api.FailureException::class)
    fun handlePostLink(link: String, idealSize: Int) {
        val html = Api.html(link)
        val data = RelayPrefetchedStreamCache.crawl(html) { // hashMapOf<String, Map<String, Any>>()
            it.contains("PolarisPostRootQueryRelayPreloader")
        }
        if (System.getenv("debug") == "1")
            println("RelayPrefetchedStreamCache: " + data.keys.joinToString(", "))

        if ("PolarisPostRootQueryRelayPreloader" in data) {
            @Suppress("UNCHECKED_CAST")
            val medMap =
                (data["PolarisPostRootQueryRelayPreloader"]!!["items"] as List<Map<String, Any>>)[0]
            downloadTask.download(
                Api.json.decodeFromString<Media>(Api.json.encodeToString(medMap)), idealSize, link
            )
        } else if ("instagram://media?id=" in html) {
            val medId = html.substringAfter("instagram://media?id=").substringBefore("\"")
            if (System.getenv("debug") == "1")
                println("Media ID: $medId")
            val singleItemList =
                Api.json<Rest.LazyList<Media>>(Api.Endpoint.MEDIA_INFO.url.format(medId))
            downloadTask.download(singleItemList.items.first(), idealSize, link)
        } else
            if (System.getenv("debug") == "1")
                System.err.println("Shall we re-implement PageConfig?")
    }

    /**
     * Performs any of the actions specified in [GraphQlQuery] concerning [Media].
     * @throws Api.FailureException
     */
    @Throws(Api.FailureException::class)
    fun actionMedia(med: Media, graphQlQuery: GraphQlQuery) {
        when (graphQlQuery) {
            GraphQlQuery.LIKE_POST, GraphQlQuery.LIKE_STORY -> if (med.has_liked == true) {
                println("Already liked ${med.link()}")
                return; }

            GraphQlQuery.UNLIKE_POST, GraphQlQuery.UNLIKE_STORY -> if (med.has_liked == false) {
                println("Already haven't liked ${med.link()}")
                return; }

            GraphQlQuery.SAVE -> if (med.has_viewer_saved == true) {
                println("Already saved ${med.link()}")
                return; }

            GraphQlQuery.UNSAVE -> if (med.has_viewer_saved == false) {
                println("Already haven't saved ${med.link()}")
                return; }

            else -> throw IllegalArgumentException("Unsupported action!")
        }
        SimpleJobs.actionMedia(med, graphQlQuery) { success ->
            val verb = when (graphQlQuery) {
                GraphQlQuery.LIKE_POST, GraphQlQuery.LIKE_STORY -> "like"
                GraphQlQuery.UNLIKE_POST, GraphQlQuery.UNLIKE_STORY -> "unlike"
                GraphQlQuery.SAVE -> "save"
                GraphQlQuery.UNSAVE -> "unsave"
                else -> throw IllegalArgumentException("Unsupported action!")
            }
            if (success) println("Successfully ${verb}d ${med.link()}")
            else System.err.println("Could not $verb ${med.link()}")
        }
    }
}
