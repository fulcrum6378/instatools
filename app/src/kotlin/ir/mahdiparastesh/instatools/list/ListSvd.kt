package ir.mahdiparastesh.instatools.list

import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.Expandable
import java.util.concurrent.CopyOnWriteArrayList

class ListSvd(c: Main, f: PageSvd) : ListMedia<Main, PageSvd>(c, f) {
    override val media: CopyOnWriteArrayList<Media>?
        get() = c.mm.saved?.items?.let { CopyOnWriteArrayList(it.map { s -> s.media }) }
    override val inflater: LayoutInflater by lazy { f.inflater }
    override val tracker: SelectionTracker<String>? get() = f.tracker
    override val expandable: Expandable by lazy {
        Expandable(
            c, expanded, c.color(if (!c.night()) R.color.defBG else R.color.CS)
        ) { f.updateShadow() }
    }
    override val expanded: ExpandableBinding = f.b.expanded
}
