package ir.mahdiparastesh.instatools.list

import android.os.Handler
import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.view.Expandable

class ListVwr(c: Viewer, private val f: PageVwr) : ListPost<Viewer>(c) {
    override val edges: ArrayList<Profile.EdgePost>? get() = c.m.vwUser?.edges()
    override val inflater: LayoutInflater by lazy { c.layoutInflater }
    override val tracker: SelectionTracker<String>? get() = f.tracker
    override val handler: Handler? get() = Viewer.handler
    override val expandable: Expandable get() = c.expandable
    override val expanded: ExpandableBinding = c.b.expanded

    override fun selective(status: Boolean) {
        c.b.toolbar.menu.clear()
        c.b.toolbar.inflateMenu(if (status) R.menu.viewer_tlb_select else R.menu.viewer_tlb)
        c.fixTbMenu()
    }
}
