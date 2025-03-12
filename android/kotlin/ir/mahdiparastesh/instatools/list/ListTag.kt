package ir.mahdiparastesh.instatools.list

import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.view.Expandable

class ListTag(c: Viewer, f: PageTag) : ListPost<Viewer, PageTag>(c, f) {
    override val inflater: LayoutInflater by lazy { c.layoutInflater }
    override val tracker: SelectionTracker<Long>? get() = f.tracker
    override val expandable: Expandable get() = c.expandable
    override val expanded: ExpandableBinding = c.b.expanded

    override fun get(position: Int): Media? =
        c.vm.tagged?.edges?.getOrNull(position)?.node

    override fun getItemCount(): Int =
        c.vm.tagged?.edges?.size ?: 0
}
