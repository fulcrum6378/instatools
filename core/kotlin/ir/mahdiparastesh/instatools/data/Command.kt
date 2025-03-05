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

    /** Check if this [Command] is even valid. */
    fun needsHandling(): Boolean {
        if (save && media.has_viewer_saved == true) save = false
        if (unsave && media.has_viewer_saved == false) unsave = false
        if (like && media.has_liked == true) like = false
        if (unlike && media.has_liked == false) unlike = false
        return save || unsave || like || unlike
    }

    /** Call this when one of subcommands is executed. */
    fun done(action: GraphQlQuery) {
        when (action) {
            GraphQlQuery.SAVE -> save = false
            GraphQlQuery.UNSAVE -> unsave = false
            GraphQlQuery.LIKE_POST, GraphQlQuery.LIKE_STORY -> like = false
            GraphQlQuery.UNLIKE_POST, GraphQlQuery.UNLIKE_STORY -> unlike = false
            else -> {}
        }
    }

    companion object {
        const val HANDLE_ITEM_SAVED = 1
        const val HANDLE_ITEM_UNSAVED = 2
        const val HANDLE_ITEM_LIKED = 3
        const val HANDLE_ITEM_UNLIKED = 4

        fun applyChangesOnMedia(med: Media, change: Int) {
            when (change) {
                HANDLE_ITEM_SAVED -> med.has_viewer_saved = true
                HANDLE_ITEM_UNSAVED -> med.has_viewer_saved = false
                HANDLE_ITEM_LIKED -> med.has_liked = true
                HANDLE_ITEM_UNLIKED -> med.has_liked = false
            }
        }

        fun message(post: GraphQlQuery): Int = when (post) {
            GraphQlQuery.SAVE -> HANDLE_ITEM_SAVED
            GraphQlQuery.UNSAVE -> HANDLE_ITEM_UNSAVED
            GraphQlQuery.LIKE_POST, GraphQlQuery.LIKE_STORY -> HANDLE_ITEM_LIKED
            GraphQlQuery.UNLIKE_POST, GraphQlQuery.UNLIKE_STORY -> HANDLE_ITEM_UNLIKED
            else -> throw IllegalArgumentException()
        }
    }
}
