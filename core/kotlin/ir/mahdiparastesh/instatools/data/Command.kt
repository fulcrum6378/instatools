package ir.mahdiparastesh.instatools.data

import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import kotlinx.serialization.Serializable

@Serializable
data class Command(
    val media: Media,
    var save: Boolean = false,
    var unsave: Boolean = false,
    var like: Boolean = false,
    var unlike: Boolean = false,
) {

    fun graphQl() = arrayListOf<GraphQlQuery>().apply {
        if (save) add(GraphQlQuery.SAVE)
        if (unsave) add(GraphQlQuery.UNSAVE)
        if (like) add(if (media.isPost()) GraphQlQuery.LIKE_POST else GraphQlQuery.LIKE_STORY)
        if (unlike) add(if (media.isPost()) GraphQlQuery.UNLIKE_POST else GraphQlQuery.UNLIKE_STORY)
    }

    fun done(action: GraphQlQuery) {
        when (action) {
            GraphQlQuery.SAVE -> save = false
            GraphQlQuery.UNSAVE -> unsave = false
            GraphQlQuery.LIKE_POST, GraphQlQuery.LIKE_STORY -> like = false
            GraphQlQuery.UNLIKE_POST, GraphQlQuery.UNLIKE_STORY -> unlike = false
            else -> {}
        }
    }
}
