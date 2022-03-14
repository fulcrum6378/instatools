package ir.mahdiparastesh.instatools.view

import androidx.recyclerview.selection.SelectionTracker

interface Selective {
    var tracker: SelectionTracker<String>?
    var selectivity: Boolean

    fun buildSelection()
}
