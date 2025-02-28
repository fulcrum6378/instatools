package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import kotlinx.serialization.Serializable

@Serializable
data class Command(
    val media: Media,
    private val save: Boolean = false,
    private val unsave: Boolean = false,
    private val like: Boolean = false,
    private val unlike: Boolean = false,
) {
    fun graphQl() = arrayListOf<GraphQlQuery>().apply {
        if (save) add(GraphQlQuery.SAVE)
        if (unsave) add(GraphQlQuery.UNSAVE)
        if (like)
            add(if (media.isPost()) GraphQlQuery.LIKE_POST else GraphQlQuery.LIKE_STORY)
        if (unlike)
            add(if (media.isPost()) GraphQlQuery.UNLIKE_POST else GraphQlQuery.UNLIKE_STORY)
    }
}
