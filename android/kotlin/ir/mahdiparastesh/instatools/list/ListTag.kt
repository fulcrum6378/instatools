package ir.mahdiparastesh.instatools.list

import android.view.LayoutInflater
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.DownloadHistory
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.view.Expandable

class ListTag(c: Viewer, f: PageTag) : ListLazyPost<Viewer, PageTag>(c, f) {

    override val inflater: LayoutInflater by lazy { c.layoutInflater }
    override val tracker: SelectionTracker<Long>? get() = f.tracker
    override val expandable: Expandable get() = c.expandable
    override val expanded: ExpandableBinding = c.b.expanded

    override fun get(position: Int): Media? =
        c.vm.tagged?.edges?.getOrNull(position)?.node

    override fun getItemCount(): Int =
        c.vm.tagged?.edges?.size ?: 0

    override fun isDownloaded(med: Media, downloadHistory: DownloadHistory): Boolean =
        med.carousel_media?.any { car ->
            c.c.downloadHistory.anyContains("_${car.id()}.")
        } ?: c.c.downloadHistory.anyContains("_${med.id()}.")

    override operator fun set(position: Int, item: Media) {
        c.vm.tagged?.apply {
            edges.getOrNull(position)?.node = item
            Pickle(c.cacheDir, c.c.acc!!.id, Pickle.Type.TAGGED, c.vm.profile!!.id!!).save(this)
        }
    }
}
