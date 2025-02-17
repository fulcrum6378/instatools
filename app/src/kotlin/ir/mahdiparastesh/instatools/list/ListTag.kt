package ir.mahdiparastesh.instatools.list

import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.view.Expandable
import java.util.concurrent.CopyOnWriteArrayList

class ListTag(c: Viewer, f: PageTag) : ListMedia<Viewer, PageTag>(c, f) {
    override val media: CopyOnWriteArrayList<Media>? get() = c.mm.vwTagged?.items
    override val inflater: LayoutInflater by lazy { c.layoutInflater }
    override val tracker: SelectionTracker<String>? get() = f.tracker
    override val expandable: Expandable get() = c.expandable
    override val expanded: ExpandableBinding = c.b.expanded
}
