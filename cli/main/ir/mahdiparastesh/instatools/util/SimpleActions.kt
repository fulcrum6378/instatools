package ir.mahdiparastesh.instatools.util

import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.job.SimpleJobs

object SimpleActions {
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
