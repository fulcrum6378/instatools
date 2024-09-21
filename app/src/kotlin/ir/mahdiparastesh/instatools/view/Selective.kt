package ir.mahdiparastesh.instatools.view

import androidx.recyclerview.selection.SelectionTracker

/** Helper interface for selection mode of RecyclerView. */
interface Selective {
    var tracker: SelectionTracker<String>?
    var selectivity: Boolean

    fun buildSelection()
}
