package ir.mahdiparastesh.instatools.list

import android.os.Handler
import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.view.Expandable

class ListSvd(c: Main, private val f: PageSvd) : ListPost<Main>(c) {
    override val edges: ArrayList<Profile.EdgePost>? get() = c.m.saved?.edges
    override val inflater: LayoutInflater by lazy { f.inflater }
    override val tracker: SelectionTracker<String>? get() = f.tracker
    override val handler: Handler? get() = f.handler
    override val expandable: Expandable by lazy {
        Expandable(c, expanded, handler, c.color(if (!c.night()) R.color.defBG else R.color.CSD))
    }
    override val expanded: ExpandableBinding = f.b.expanded

    override fun selective(status: Boolean) {
        c.selective(status)
    }
}
