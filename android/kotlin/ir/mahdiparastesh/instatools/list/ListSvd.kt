package ir.mahdiparastesh.instatools.list

import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.view.Expandable

class ListSvd(c: Main, f: PageSvd) : ListPost<Main, PageSvd>(c, f) {
    override val inflater: LayoutInflater by lazy { f.inflater }
    override val tracker: SelectionTracker<Long>? get() = f.tracker
    override val expandable: Expandable get() = f.expandable
    override val expanded: ExpandableBinding = f.b.expanded

    override fun get(position: Int): Media? =
        c.vm.saved?.items?.getOrNull(position)?.media

    override fun getItemCount(): Int =
        c.vm.saved?.items?.size ?: 0
}
