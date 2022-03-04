package ir.mahdiparastesh.instatools.list

import android.os.Handler
import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.json.Profile

class ListVwr(c: Viewer) : ListPost<Viewer>(c) {
    override val edges: ArrayList<Profile.EdgePost>? get() = c.m.vwUser?.edges()
    override val inflater: LayoutInflater by lazy { c.layoutInflater }
    override val tracker: SelectionTracker<String>? get() = c.tracker
    override val handler: Handler? get() = Viewer.handler
    override val expanded: ExpandableBinding = c.b.expanded

    override fun selective(status: Boolean) {
        c.b.toolbar.menu.clear()
        c.b.toolbar.inflateMenu(if (status) R.menu.viewer_tlb_select else R.menu.viewer_tlb)
        c.fixTbMenu()
    }
}
