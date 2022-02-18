package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.*
import androidx.core.view.contains
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
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.BaseSaver
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

class PageSvd(c: Main) : BasePage(c) {
    lateinit var b: PageSvdBinding
    private var thread: FetchSome? = null
    override lateinit var inflater: LayoutInflater
    override var handler: Handler? = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_FETCHED -> {
                    onLoaded()
                    if (!c.m.saved.isNullOrEmpty() && c.m.nextSaved?.has_next_page == true &&
                        !b.rv.canScrollVertically(1)
                    ) thread = FetchSome().also { it.start() }
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
                HANDLE_UNSAVE_DONE -> c.m.saved?.find { it.id == msg.obj as String }?.let { post ->
                    val x = c.m.saved!!.indexOf(post)
                    c.m.saved!!.removeAt(x)
                    b.rv.adapter?.notifyItemRemoved(x)
                    b.rv.adapter?.notifyItemRangeChanged(x, c.m.saved!!.size)
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
        inflater = c.themeInflater(BaseActivity.Theme.SECONDARY)
        b = PageSvdBinding.inflate(
            c.themeInflater(BaseActivity.Theme.SECONDARY, inf), parent, false
        )
        if (Main.guest) {
            b.refresher.isEnabled = false
            guestMode(b.root, BaseActivity.Theme.SECONDARY); return b.root; }

        b.rv.layoutManager = GridLayoutManager(c, 3)
        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback tracker?.hasSelection() == true
        }
        b.refresher.setOnRefreshListener {
            if (thread?.active == true) return@setOnRefreshListener
            b.rv.adapter = null
            c.m.nextSaved = null
            c.m.saved = null
            tracker = null
            thread = FetchSome().also { it.start() }
        }
        b.rv.viewTreeObserver.addOnScrollChangedListener {
            if (!b.rv.canScrollVertically(1) &&
                thread?.active != true && c.m.nextSaved?.has_next_page != false
            ) thread = FetchSome().also { it.start() }
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
                updateJumper()
            }
        })

        //b.refresher.isRefreshing = true
        if (c.m.saved != null) onLoaded()
        else if (thread == null) thread = FetchSome().also { it.start() }
        return b.root
    }

    override fun onFailed(message: String) {
        b.refresher.isRefreshing = false
        try {
            Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
        } catch (ignored: IllegalArgumentException) {
            // No suitable parent found from the given view. Please provide a valid view.
        }
        if (b.root.contains(b.loading)) {
            b.loading.animation?.cancel()
            b.root.removeView(b.loading)
        }
        if (b.rv.adapter == null) b.error.vis()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded() {
        b.refresher.isRefreshing = false
        if (b.root.contains(b.loading)) {
            b.loading.animation?.cancel()
            b.root.removeView(b.loading)
        }
        b.error.vis(false)

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
            if (c.m.saved != null) tracker?.setItemsSelected(c.m.saved!!.map { it.id }, true)
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
        override fun getKey(i: Int): String = c.m.saved!![i].id
        override fun getPosition(key: String): Int {
            for (i in c.m.saved!!.indices) if (c.m.saved!![i].id == key) return i
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
            if (c.m.nextSaved?.has_next_page == false || c.m.acc == null) return
            if (c.m.saved == null) Api<Profile>(
                c, Api.Type.SAVED_FIRST.url.format(c.m.acc!!.user), Profile::class, handler
            ) { profile ->
                val edgeList = profile.graphql?.user?.edge_saved_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget(); return@Api; }
                c.m.saved = arrayListOf()
                done(edgeList)
            } else Api<Profile.GraphQlResponse>(
                c, Api.Type.SAVED.url.format(
                    c.m.acc!!.id, c.m.saved!!.size, c.m.nextSaved?.end_cursor ?: ""
                ), Profile.GraphQlResponse::class, handler
            ) { res ->
                val edgeList = res.data.user?.edge_saved_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget(); return@Api; }
                done(edgeList)
            }
        }

        private fun done(media: Profile.EdgeList) {
            c.m.nextSaved = media.page_info
            media.edges.map { it.node }.forEach { post ->
                c.m.saved?.removeAll { it.id == post.id }
                c.m.saved?.add(post)
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
            c.m.saved?.find { it.id == svd }?.let { post ->
                if (download) c.dao.addQueued(
                    Queued(Queuer.now(), Api.Type.POST.url.format(post.shortcode))
                )
                if (unsave) Api<Rest>(
                    c, Api.Type.UNSAVE.url.format(post.id), Rest::class, handler,
                    method = Request.Method.POST
                ) { rest ->
                    if (rest.status == "ok")
                        handler?.obtainMessage(HANDLE_UNSAVE_DONE, svd)?.sendToTarget()
                    ended()
                }
            }
            if (!unsave) ended()
        }
    }
}
