package ir.mahdiparastesh.instatools.list

import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.view.Expandable

class ListVwr(c: Viewer, f: PageVwr) : ListPost<Viewer, PageVwr>(c, f) {
    override val inflater: LayoutInflater by lazy { c.layoutInflater }
    override val tracker: SelectionTracker<String>? get() = f.tracker
    override val expandable: Expandable get() = c.expandable
    override val expanded: ExpandableBinding = c.b.expanded

    override fun get(position: Int): Media? =
        c.mm.posts?.edges?.getOrNull(position)?.node

    override fun getItemCount(): Int =
        c.mm.posts?.edges?.size ?: 0
}
