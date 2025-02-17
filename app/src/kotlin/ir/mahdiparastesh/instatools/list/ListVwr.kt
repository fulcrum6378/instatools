package ir.mahdiparastesh.instatools.list

import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.view.Expandable
import java.util.concurrent.CopyOnWriteArrayList

class ListVwr(c: Viewer, f: PageVwr) : ListEdge<Viewer, PageVwr>(c, f) {
    override val edges: CopyOnWriteArrayList<GraphQl.EdgePost>? get() = c.mm.user?.edges()
    override val inflater: LayoutInflater by lazy { c.layoutInflater }
    override val tracker: SelectionTracker<String>? get() = f.tracker
    override val expandable: Expandable get() = c.expandable
    override val expanded: ExpandableBinding = c.b.expanded
}
