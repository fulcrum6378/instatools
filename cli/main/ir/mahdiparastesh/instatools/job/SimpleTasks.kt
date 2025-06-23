package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media

object SimpleTasks {

    /** Performs any of the actions specified in [GraphQlQuery] related to [Media]. */
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

    /** Fetches a list of stories in one's feed without their [Media]s. */
    fun feedTray(maxSpans: Int = 3) {
        val gql = Api.graphQl(GraphQlQuery.FEED_TRAY.body())
        val sb = StringBuilder()
        var span = 0
        var u: String
        for (story in gql.data!!.xdt_api__v1__feed__reels_tray!!.tray) {
            u = "@${story.user.username}"
            if (span != maxSpans - 1)
                sb.append(u.padEnd(32))
            else
                sb.append("$u\n")
            span++
            if (span == maxSpans) span = 0
        }
        println(sb.toString())
        println("Type `r <@USERNAME>` to fetch a list of their stories...")
    }
}
