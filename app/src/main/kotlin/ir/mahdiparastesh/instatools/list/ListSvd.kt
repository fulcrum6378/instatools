package ir.mahdiparastesh.instatools.list

import android.os.Handler
import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.json.GraphQl
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.Expandable
import java.util.concurrent.CopyOnWriteArrayList

class ListSvd(c: Main, f: PageSvd) : ListEdge<Main, PageSvd>(c, f) {
    override val edges: CopyOnWriteArrayList<GraphQl.EdgePost>? get() = c.mm.saved?.edges
    override val inflater: LayoutInflater by lazy { f.inflater }
    override val tracker: SelectionTracker<String>? get() = f.tracker
    override val handler: Handler? get() = PageSvd.handler
    override val expandable: Expandable by lazy {
        Expandable(
            c, expanded, handler, f.reqQueue, c.color(if (!c.night()) R.color.defBG else R.color.CS)
        ) { f.updateShadow() }
    }
    override val expanded: ExpandableBinding = f.b.expanded
}
