package ir.mahdiparastesh.instatools.frag

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.base.BasePageViewer
import ir.mahdiparastesh.instatools.base.ForegroundService
import ir.mahdiparastesh.instatools.base.PostSelector
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageRelBinding
import ir.mahdiparastesh.instatools.list.ListRel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PageRel : BasePageViewer(), PostSelector {
    private lateinit var b: PageRelBinding

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override var tracker: SelectionTracker<Long>? = null
    override var selectivity = false
    override val dialogContext: Context get() = c

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.vm.reels != null
    override fun isModelEmpty(): Boolean = c.vm.reels?.edges?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListRel(c, this)
    override fun canLoadMore(): Boolean = c.vm.reels?.page_info?.has_next_page != false

    companion object {
        var handler: Handler? = null
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageRelBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // handler
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    ForegroundService.HANDLE_ITEM_UPDATED -> {
                    }
                }
            }
        }
    }

    override suspend fun fetch(reset: Boolean) {

        // first read from cache if available
        val pickle = Pickle(c.cacheDir, c.c.acc!!.id, Pickle.Type.REELS, c.vm.user!!.id!!)
        val cache =
            if (c.vm.reels == null && !reset) pickle.restore<Page<Media.Wrapper>>() else null
        if (cache != null) {
            c.vm.reels = cache
            withContext(Dispatchers.Main) { onLoaded() }
            return; }

        // fetch online reels
        val cursor = if (!reset) c.vm.reels?.page_info?.end_cursor else null
        val page = Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            if (cursor == null)
                GraphQlQuery.PROFILE_REELS_INITIAL.body(c.vm.user!!.id!!, "12")
            else
                GraphQlQuery.PROFILE_REELS_MORE.body(c.vm.user!!.id!!, "12", cursor)
        ).data!!.xdt_api__v1__clips__user__connection_v2!!

        // update the data model and the UI
        if (c.vm.reels == null || reset) {
            c.vm.reels = page
            withContext(Dispatchers.Main) { onLoaded() }
        } else c.vm.reels?.apply {
            val lastBefore = edges.size
            edges.addAll(page.edges)
            page_info.has_next_page = page.page_info.has_next_page
            withContext(Dispatchers.Main) { onLazilyLoaded(lastBefore, edges.size) }
        }

        // cache the data model
        c.vm.reels?.also { pickle.save(it) }
    }

    override fun selectionKeyProvider() = object : ItemKeyProvider<Long>(SCOPE_MAPPED) {
        override fun getKey(i: Int): Long? = c.vm.reels?.edges?.getOrNull(i)?.node?.media?.uid
        override fun getPosition(key: Long): Int {
            c.vm.reels?.edges?.forEachIndexed { i, edge ->
                if (edge.node.media.uid == key) return@getPosition i
            }
            return -1
        }
    }

    override fun selectionObserver(): SelectionTracker.SelectionObserver<Long>? =
        createSelectionObserver()

    override fun onSelectionStarted() {
        (b.rv.adapter as ListRel).firstLongClickSelect = true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload ->
                enqueueSelectedMedia(c, c.vm.reels, download = true)
            R.id.vtLike ->
                enqueueSelectedMedia(c, c.vm.reels, like = true)
            R.id.vtUnlike ->
                enqueueSelectedMedia(c, c.vm.reels, unlike = true)
            R.id.vtSave ->
                enqueueSelectedMedia(c, c.vm.reels, save = true)
            R.id.vtUnsave ->
                enqueueSelectedMedia(c, c.vm.reels, unsave = true)

            R.id.vtSelectAll ->
                if (c.vm.reels?.edges != null)
                    tracker?.setItemsSelected(c.vm.reels!!.edges.map { it.node.media.uid }, true)
            R.id.vtDeselectAll ->
                tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    override fun goBack(): Boolean {
        return onGoBackWithSelection()
    }

    override fun onDestroy() {
        handler = null
        super.onDestroy()
    }
}
