package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.*
import android.widget.Toast
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
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.UiTools

class PageSvd(c: Main) : BasePage(c) {
    lateinit var b: PageSvdBinding
    private var thread: FetchSome? = null
    override var inflater: LayoutInflater = c.themeInflater(BaseActivity.Theme.SECONDARY)
    override var handler: Handler? = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_FETCHED -> {
                    adapt()
                    b.refresher.isRefreshing = false
                    if (!c.m.saved.isNullOrEmpty() && c.m.nextSaved?.has_next_page == true &&
                        (c.m.saved!!.size / 3) * (c.dm.widthPixels / 3) < c.dm.heightPixels
                    ) thread = FetchSome().also { it.start() }
                }
                HANDLE_ABORTED -> {
                    b.refresher.isRefreshing = false
                    Snackbar.make(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG).show()
                }
                Api.HANDLE_ERROR -> {
                    b.refresher.isRefreshing = false
                    Snackbar.make(
                        b.root, c.getString(
                            R.string.unknownError,
                            (msg.obj as NetworkResponse).statusCode.toString()
                        ), Snackbar.LENGTH_SHORT
                    ).show()
                }
                HANDLE_EXPANDABLE_ERROR ->
                    Snackbar.make(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG).show()
                HANDLE_UNSAVE_DONE -> c.m.saved?.find { it.id == msg.obj as String }?.let { post ->
                    val x = c.m.saved!!.indexOf(post)
                    c.m.saved!!.removeAt(x)
                    b.rv.adapter?.notifyItemRemoved(x)
                    b.rv.adapter?.notifyItemRangeChanged(x, c.m.saved!!.size)
                    if (x > 0) b.rv.adapter?.notifyItemChanged(x - 1)
                }
            }
        }
    }
    var tracker: SelectionTracker<String>? = null
    private var selectivity = false

    companion object {
        const val HANDLE_EXPANDABLE_ERROR = 10
        const val HANDLE_UNSAVE_DONE = 11
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageSvdBinding.inflate(
            c.themeInflater(BaseActivity.Theme.SECONDARY, inf), parent, false
        )
        if (Main.guest) {
            guestMode(b.root, BaseActivity.Theme.SECONDARY); return b.root; }

        b.rv.layoutManager = GridLayoutManager(c, 3)
        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback tracker?.hasSelection() == true
        }
        b.refresher.setOnRefreshListener {
            b.rv.adapter = null
            c.m.nextSaved = null
            c.m.saved = null
            tracker = null
            if (thread?.active != true) thread = FetchSome().also { it.start() }
        }
        b.rv.viewTreeObserver.addOnScrollChangedListener {
            if ((b.rv.computeVerticalScrollExtent() + b.rv.computeVerticalScrollOffset() +
                        (c.dm.heightPixels * 0.1)) >= b.rv.computeVerticalScrollRange() &&
                thread?.active != true && c.m.nextSaved?.has_next_page != false
            ) thread = FetchSome().also { it.start() }
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
            }
        })
        if (c.m.saved != null) adapt()
        else thread?.start()

        return b.root
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter != null) {
            b.rv.adapter?.notifyDataSetChanged()
            return; }
        b.rv.adapter = ListSvd(c, this)
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
            if (tracker != null && c.m.saved != null)
                Saver(tracker!!.selection, unsave = true, download = true).start()
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
            if (tracker != null && c.m.saved != null)
                Saver(tracker!!.selection, unsave = false, download = true).start()
            tracker?.clearSelection()
            true
        }
        R.id.mtUnsave -> {
            if (tracker != null && c.m.saved != null)
                Saver(tracker!!.selection, unsave = true, download = false).start()
            tracker?.clearSelection()
            true
        }
        else -> false
    }

    override fun updateShadow() {
        UiTools.vish(c.b.tbShadow, b.rv.computeVerticalScrollOffset() > 0)
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
            Toast.makeText(c, "${tracker?.hasSelection()}", Toast.LENGTH_SHORT).show()
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
            if (c.m.nextSaved?.has_next_page == false) return
            if (c.m.saved == null) Api<Profile>(
                c, Api.Type.SAVED_FIRST.url.format(c.m.acc!!.user), Profile::class, handler
            ) { profile ->
                val med = profile.graphql?.user?.edge_saved_media
                if (med == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget(); return@Api; }
                c.m.saved = arrayListOf()
                done(med)
            } else Api<Profile.GraphQlResponse>(
                c, Api.Type.SAVED.url.format(
                    c.m.acc!!.id, c.m.saved!!.size, c.m.nextSaved?.end_cursor ?: ""
                ), Profile.GraphQlResponse::class, handler
            ) { res ->
                val med = res.data.user?.edge_saved_media
                if (med == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget(); return@Api; }
                done(med)
            }
        }

        private fun done(media: Profile.Media) {
            c.m.nextSaved = media.page_info
            media.edges.map { it.node }.forEach { post ->
                c.m.saved?.removeAll { it.id == post.id }
                c.m.saved?.add(post)
            }
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            interrupt()
        }
    }

    inner class Saver(selection: Selection<String>, val unsave: Boolean, val download: Boolean) :
        Thread() {
        private val list = ArrayList(selection.toList())

        override fun run() {
            handle()
        }

        private fun handle() {
            val svd = list.getOrNull(0)
            if (svd == null) {
                if (download) Downloads.initService(c)
                return
            }
            c.m.saved?.find { it.id == svd }?.let { post ->
                if (download) c.pDao.addQueued(
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

        private fun ended() {
            list.removeAt(0)
            handle()
        }
    }
}
