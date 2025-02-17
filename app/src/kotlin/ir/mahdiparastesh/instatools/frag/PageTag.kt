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
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.PageTagBinding
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Media.Wrapper
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListTag
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageTag : BasePageViewer() {
    private lateinit var b: PageTagBinding
    private var thread: Job? = null

    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageTagBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // List
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!b.rv.canScrollVertically(1)) fetchSome()
            }
        })

        // Error
        b.error.setOnClickListener {
            c.b.refresher.isRefreshing = true
            fetchSome()
        }

        load()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload -> {
                if (tracker != null && c.mm.vwTagged?.items != null)
                    Saver(tracker!!.selection)
                tracker?.clearSelection()
            }
            R.id.vtSelectAll -> if (c.mm.vwTagged?.items != null)
                tracker?.setItemsSelected(c.mm.vwTagged!!.items!!.map { it.id }, true)
            R.id.vtDeselectAll -> tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    fun load() {
        if (c.mm.vwTagged != null)
            onLoaded(c.mm.vwTagged?.items.isNullOrEmpty())
        else fetchSome()
    }

    private fun fetchSome() {
        if (c.mm.vwUser == null) {
            c.mm.vwTagged = null
            return; }
        if (thread != null || c.mm.vwTagged?.more_available == false) {
            return; }

        thread = CoroutineScope(Dispatchers.IO).launch {
            val wrapper = Api.call<Wrapper>(
                Api.Endpoint.TAGGED.url.format(
                    c.mm.vwUser?.id ?: "", c.mm.vwTagged?.next_max_id ?: ""
                ), Wrapper::class, onError = { code ->
                    UiTools.snackbar(b.root, Api.error(code), Snackbar.LENGTH_LONG)
                }
            )
            if (wrapper == null) {
                thread = null
                return@launch; }

            if (c.mm.vwTagged == null) {
                c.mm.vwTagged = wrapper
                withContext(Dispatchers.Main) {
                    onLoaded(c.mm.vwTagged?.items.isNullOrEmpty())
                    if (!b.rv.canScrollVertically(1)) fetchSome()
                }
            } else c.mm.vwTagged?.apply {
                val lastBefore = items?.size ?: 0
                val ids = items?.map { it.id }
                wrapper.items
                    ?.let { if (ids != null) it.filter { p -> p.id !in ids } else it }
                    ?.let { items?.addAll(it) }
                next_max_id = wrapper.next_max_id
                more_available = wrapper.more_available
                withContext(Dispatchers.Main) {
                    b.rv.adapter?.notifyItemRangeInserted(lastBefore, items?.size ?: 0)
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
        override fun getKey(i: Int): String? = c.mm.vwTagged?.items?.getOrNull(i)?.id
        override fun getPosition(key: String): Int {
            c.mm.vwTagged?.items?.forEachIndexed { i, med ->
                if (med.id == key) return@getPosition i
            }
            return -1
        }
    }

    inner class Saver(selection: Selection<String>) : SelectionHandler(selection) {

        override suspend fun handle() {
            val post = next()
            if (post == null) {
                Downloads.initService(c)
                return
            }
            c.mm.vwTagged?.items?.find { it.id == post }?.queue(c.dao)
            ended()
        }
    }
}
