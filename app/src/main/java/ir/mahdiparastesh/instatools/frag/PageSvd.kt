package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.selection.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.PageSvdBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

class PageSvd(c: Main) : BasePage(c) {
    lateinit var b: PageSvdBinding
    private var thread: FetchSome? = null
    override lateinit var inflater: LayoutInflater
    override val root: ConstraintLayout get() = b.root
    override var handler: Handler? = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_FETCHED -> {
                    onLoaded(c.m.saved?.edges.isNullOrEmpty())
                    if (c.m.saved != null && !b.rv.canScrollVertically(1))
                        thread = FetchSome().also { it.start() }
                }
                HANDLE_ABORTED -> onFailed(c.getString(R.string.loadFailed))
                HANDLE_INIT_QUEUER -> Downloads.initService(c)
                Api.HANDLE_ERROR -> onFailed(
                    c.getString(
                        R.string.unknownError, (msg.obj as NetworkResponse?)?.statusCode.toString()
                    )
                )
                Expandable.HANDLE_EXPANDABLE_ERROR -> try {
                    Snackbar.make(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG).show()
                } catch (ignored: IllegalArgumentException) {
                }
                HANDLE_UNSAVE_DONE -> c.m.saved?.edges?.find { it.node.id == msg.obj as String }
                    ?.let { post ->
                        val x = c.m.saved!!.edges.indexOf(post)
                        c.m.saved!!.edges.removeAt(x)
                        b.rv.adapter?.notifyItemRemoved(x)
                        b.rv.adapter?.notifyItemRangeChanged(x, c.m.saved!!.edges.size)
                    }
            }
        }
    }
    var tracker: SelectionTracker<String>? = null
    private var selectivity = false

    companion object {
        const val HANDLE_UNSAVE_DONE = 10
        const val HANDLE_INIT_QUEUER = 11
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        inflater = c.themeInflater(BaseActivity.Theme.SECONDARY, inf)
        b = PageSvdBinding.inflate(inflater, parent, false)
        if (Main.guest) {
            guestMode(b.root, BaseActivity.Theme.SECONDARY); return b.root; }

        b.rv.layoutManager = GridLayoutManager(c, 3)
        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback tracker?.hasSelection() == true
        }
        b.refresher.setOnRefreshListener {
            if (thread?.active == true) return@setOnRefreshListener
            b.rv.adapter = null
            c.m.saved = null
            tracker = null
            thread = FetchSome().also { it.start() }
        }
        b.rv.viewTreeObserver.addOnScrollChangedListener {
            if (!b.rv.canScrollVertically(1) &&
                thread?.active != true && c.m.saved?.page_info?.has_next_page != false
            ) thread = FetchSome().also { it.start() }
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
                updateJumper()
            }
        })

        //b.refresher.isRefreshing = true
        if (c.m.saved != null) onLoaded(c.m.saved?.edges.isNullOrEmpty())
        else if (thread == null) thread = FetchSome().also { it.start() }
        return b.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
        if (!asGuest) c.bnvBadge(1, c.m.saved?.count?.toInt() ?: 0) // TODO: NOT ACTUAL
        if (b.rv.adapter == null) b.rv.adapter = ListSvd(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
        if (tracker == null) {
            tracker = SelectionTracker.Builder(
                "saved", b.rv,
                MyItemKeyProvider(), MyDetailsLookup(),
                StorageStrategy.createStringStorage()
            ).build()
            tracker?.addObserver(SelectObserver())
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.mtUnsaveDownload -> {
            if (tracker != null && c.m.saved != null) Saver(
                tracker!!.selection, unsave = true, download = true
            ).start()
            tracker?.clearSelection()
            true
        }
        R.id.mtSelectAll -> {
            if (c.m.saved != null)
                tracker?.setItemsSelected(c.m.saved!!.edges.map { it.node.id }, true)
            true
        }
        R.id.mtDeselectAll -> {
            tracker?.clearSelection()
            true
        }
        R.id.mtDownload -> {
            if (tracker != null && c.m.saved != null) Saver(
                tracker!!.selection, unsave = false, download = true
            ).start()
            tracker?.clearSelection()
            true
        }
        R.id.mtUnsave -> {
            if (tracker != null && c.m.saved != null) Saver(
                tracker!!.selection, unsave = true, download = false
            ).start()
            tracker?.clearSelection()
            true
        }
        else -> false
    }

    override fun updateShadow() {
        c.b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
    }

    override fun updateJumper() {
    }

    override fun goBack(): Boolean {
        (b.rv.adapter as ListSvd?)?.let {
            if (it.expandable.zoomed) {
                it.expandable.collapse(); return@goBack true; }
        }
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return true
        }
        return false
    }

    inner class MyItemKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String = c.m.saved!!.edges[i].node.id
        override fun getPosition(key: String): Int {
            for (i in c.m.saved!!.edges.indices)
                if (c.m.saved!!.edges[i].node.id == key) return i
            return -1
        }
    }

    inner class MyDetailsLookup : ItemDetailsLookup<String?>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<String?>? {
            b.rv.findChildViewUnder(e.x, e.y)?.let {
                val h = b.rv.getChildViewHolder(it)
                if (h is ListSvd.ViewHolder) return@getItemDetails h.getItemDetails()
            }
            return null
        }
    }

    inner class SelectObserver : SelectionTracker.SelectionObserver<String>() {
        override fun onSelectionChanged() {
            super.onSelectionChanged()
            val status = tracker?.hasSelection() == true
            if (selectivity == status) return
            selectivity = status
            UiTools.shake(c.c)
            c.selective(selectivity)
        }
    }

    inner class FetchSome : BaseThread() {
        override fun run() {
            super.run()
            if (c.m.saved?.page_info?.has_next_page == false || c.m.acc == null) return
            if (c.m.saved == null) Api<Profile>(
                c, Api.Type.SAVED_FIRST.url.format(c.m.acc!!.user), Profile::class, handler
            ) { profile ->
                val edgeList = profile.graphql?.user?.edge_saved_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget(); return@Api; }
                c.m.saved = edgeList
                done(null)
            } else Api<Profile.GraphQlResponse>(
                c, Api.Type.SAVED.url.format(
                    c.m.acc!!.id,
                    c.m.saved!!.edges.size,
                    c.m.saved?.page_info?.end_cursor ?: ""
                ), Profile.GraphQlResponse::class, handler
            ) { res ->
                val edgeList = res.data.user?.edge_saved_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget(); return@Api; }
                done(edgeList)
            }
        }

        private fun done(add: Profile.EdgeList? = null) {
            if (add != null) {
                c.m.saved?.page_info = add.page_info
                c.m.saved?.count = add.count
                add.edges.forEach { post ->
                    c.m.saved?.edges?.removeAll { it.node.id == post.node.id }
                    c.m.saved?.edges?.add(post)
                }
            }
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            interrupt()
        }
    }

    inner class Saver(
        selection: Selection<String>, private val unsave: Boolean, private val download: Boolean
    ) : BaseSaver(selection) {

        override fun handle() {
            val svd = list.getOrNull(0)
            if (svd == null) {
                if (download) handler?.obtainMessage(HANDLE_INIT_QUEUER)?.sendToTarget()
                return
            }
            c.m.saved?.edges?.find { it.node.id == svd }?.node?.let { post ->
                if (download) c.dao.addQueued(
                    Queued(Persistent.now(), Api.Type.POST.url.format(post.shortcode))
                )
                if (unsave) Api<Rest>(
                    c, Api.Type.UNSAVE.url.format(post.id), Rest::class, null,
                    method = Request.Method.POST,
                    onError = { ended() }
                ) { rest ->
                    if (rest.status == "ok") {
                        handler?.obtainMessage(HANDLE_UNSAVE_DONE, svd)?.sendToTarget()
                        c.m.saved?.apply { if (count > 0.0) count -= 1.0 }
                        c.bnvBadge(1, c.m.saved?.count?.toInt() ?: 0)
                    }
                    ended()
                }
            }
            if (!unsave) ended()
        }
    }
}
