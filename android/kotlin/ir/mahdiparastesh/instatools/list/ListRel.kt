package ir.mahdiparastesh.instatools.list

import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.DownloadHistory
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.view.Expandable

class ListRel(c: Viewer, f: PageRel) : ListPost<Viewer, PageRel>(c, f) {
    override val inflater: LayoutInflater by lazy { c.layoutInflater }
    override val tracker: SelectionTracker<Long>? get() = f.tracker
    override val expandable: Expandable get() = c.expandable
    override val expanded: ExpandableBinding = c.b.expanded

    override fun get(position: Int): Media? =
        c.vm.reels?.edges?.getOrNull(position)?.node?.media

    override fun getItemCount(): Int =
        c.vm.reels?.edges?.size ?: 0

    override fun isDownloaded(med: Media, downloadHistory: DownloadHistory): Boolean =
        med.carousel_media?.any { car ->
            c.c.downloadHistory.anyContains("_${car.id()}.")
        } ?: c.c.downloadHistory.anyContains("_${med.id()}.")
}
