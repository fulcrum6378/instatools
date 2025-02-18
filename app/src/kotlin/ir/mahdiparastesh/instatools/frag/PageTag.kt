package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.databinding.PageTagBinding
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListTag
import ir.mahdiparastesh.instatools.util.*
import ir.mahdiparastesh.instatools.view.SelectionHandler
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageTag : BasePageViewer() {
    private lateinit var b: PageTagBinding
    private var fetcher: Job? = null

    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageTagBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // list
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!b.rv.canScrollVertically(1)) fetchSome()
            }
        })

        // error
        b.error.setOnClickListener {
            c.b.refresher.isRefreshing = true
            fetchSome()
        }

        if (c.mm.tagged != null)
            onLoaded(c.mm.tagged?.edges.isNullOrEmpty())
        else fetchSome()
    }

    private fun fetchSome() {
        if (c.mm.user == null) {
            c.mm.tagged = null
            return; }
        if (fetcher != null || c.mm.tagged?.page_info?.has_next_page == false) return

        fetcher = CoroutineScope(Dispatchers.IO).launch {
            val cursor = c.mm.tagged?.edges?.lastOrNull()?.node?.pk()
            val graphQl = Api.call<GraphQl>(
                Api.Endpoint.QUERY.url, GraphQl::class,
                isPost = true, body = if (cursor == null)
                    GraphQlQuery.PROFILE_TAGGED.body(c.mm.user!!.id!!, "36")
                else
                    GraphQlQuery.PROFILE_TAGGED_CURSORED.body(c.mm.user!!.id!!, "36", cursor),
                onError = { code ->
                    if (cursor == null) onFailed(getString(Api.error(code), code))
                    else UiTools.snackbar(b.root, getString(Api.error(code), code))
                }
            )
            if (graphQl == null) {
                fetcher = null
                return@launch; }
            val page = graphQl.data?.xdt_api__v1__usertags__user_id__feed_connection
            if (page == null) {
                withContext(Dispatchers.Main) {
                    UiTools.snackbar(b.root, R.string.invalidResponse)
                }
                fetcher = null
                return@launch; }

            if (c.mm.tagged == null) {
                c.mm.tagged = page
                withContext(Dispatchers.Main) {
                    onLoaded(c.mm.tagged?.edges.isNullOrEmpty())
                    if (!b.rv.canScrollVertically(1)) fetchSome()
                }
            } else c.mm.tagged?.apply {
                val lastBefore = edges.size
                edges.addAll(page.edges)
                page_info.has_next_page = page.page_info.has_next_page
                withContext(Dispatchers.Main) {
                    b.rv.adapter?.notifyItemRangeInserted(lastBefore, edges.size)
                    if (!b.rv.canScrollVertically(1)) fetchSome()
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean) {
        super.onLoaded(isEmpty)
        if (b.rv.adapter == null) b.rv.adapter = ListTag(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
        if (tracker == null) buildSelection()
        c.b.refresher.isRefreshing = false
    }

    override fun onFailed(message: String) {
        super.onFailed(message)
        c.b.refresher.isRefreshing = false
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload -> {
                if (tracker != null && c.mm.tagged?.edges != null)
                    Saver(tracker!!.selection)
                tracker?.clearSelection()
            }
            R.id.vtSelectAll -> if (c.mm.tagged?.edges != null)
                tracker?.setItemsSelected(c.mm.tagged!!.edges.map { it.node.pk() }, true)
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

    override fun onRecyclerViewScrolled() {
        super.onRecyclerViewScrolled()
        updateShadow()
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.mm.tagged?.edges?.getOrNull(i)?.node?.pk()
        override fun getPosition(key: String): Int {
            c.mm.tagged?.edges?.forEachIndexed { i, edge ->
                if (edge.node.pk() == key) return@getPosition i
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
            c.mm.tagged?.edges?.find { it.node.pk() == edg }?.node?.queue(c.dao)
            ended()
        }
    }
}
