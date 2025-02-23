package ir.mahdiparastesh.instatools.frag

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageTagBinding
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListTag
import ir.mahdiparastesh.instatools.util.*
import ir.mahdiparastesh.instatools.view.SelectionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PageTag : BasePageViewer() {
    private lateinit var b: PageTagBinding
    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.mm.tagged != null
    override fun isModelEmpty(): Boolean = c.mm.tagged?.edges?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListTag(c, this)
    override fun canLoadMore(): Boolean = c.mm.tagged?.page_info?.has_next_page != false

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageTagBinding.inflate(inf, parent, false).let { b = it; it.root }

    override suspend fun fetch(reset: Boolean) {
        // first read from cache if available
        val pickle = Pickle(c.cacheDir, c.m.acc!!.id, Pickle.Type.TAGGED, c.mm.user!!.id!!)
        val cache = if (c.mm.tagged == null && !reset) pickle.restore<Page<Media>>() else null
        if (cache != null) {
            c.mm.tagged = cache
            withContext(Dispatchers.Main) { onLoaded() }
            return; }

        // fetch online tagged posts
        val cursor = c.mm.tagged?.edges?.lastOrNull()?.node?.id()
        val page = Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            if (cursor == null)
                GraphQlQuery.PROFILE_TAGGED.body(c.mm.user!!.id!!, "36")
            else
                GraphQlQuery.PROFILE_TAGGED_CURSORED.body(c.mm.user!!.id!!, "36", cursor)
        ).data!!.xdt_api__v1__usertags__user_id__feed_connection!!

        // update the data model and the UI
        if (c.mm.tagged == null || reset) {
            c.mm.tagged = page
            withContext(Dispatchers.Main) { onLoaded() }
        } else c.mm.tagged?.apply {
            val lastBefore = edges.size
            edges.addAll(page.edges)
            page_info.has_next_page = page.page_info.has_next_page
            withContext(Dispatchers.Main) { onLazilyLoaded(lastBefore, edges.size) }
        }

        // cache the data model
        c.mm.tagged?.also { pickle.save(it) }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload -> {
                if (tracker != null && c.mm.tagged?.edges != null)
                    Saver(tracker!!.selection)
                tracker?.clearSelection()
            }
            R.id.vtSelectAll -> if (c.mm.tagged?.edges != null)
                tracker?.setItemsSelected(c.mm.tagged!!.edges.map { it.node.id() }, true)
            R.id.vtDeselectAll -> tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    override fun buildSelection() {
        tracker = SelectionTracker.Builder(
            "viewer_tagged", b.rv,
            PostKeyProvider(), ListPost.PostDetailsLookup(b.rv),
            StorageStrategy.createStringStorage()
        ).build().also { it.addObserver(SelectObserver()) }
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.mm.tagged?.edges?.getOrNull(i)?.node?.id()
        override fun getPosition(key: String): Int {
            c.mm.tagged?.edges?.forEachIndexed { i, edge ->
                if (edge.node.id() == key) return@getPosition i
            }
            return -1
        }
    }

    inner class Saver(selection: Selection<String>) : SelectionHandler(selection) {
        override suspend fun handle() {
            val edg = next()
            if (edg == null) {
                Downloads.initService(c)
                return
            }
            c.mm.tagged?.edges?.find { it.node.id() == edg }?.node?.queue()
                ?.also { c.m.queue.addAll(it) }
            ended()
        }
    }
}
