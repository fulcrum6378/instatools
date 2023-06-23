package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
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
import com.android.volley.NetworkResponse
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.PageTagBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.Media.Wrapper
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListTag
import ir.mahdiparastesh.instatools.more.*

class PageTag : BasePageViewer() {
    private lateinit var b: PageTagBinding
    private var thread: FetchSome? = null

    override val com: PageCompanion = Companion
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_FETCHED to { msg ->
            if (b.rv.adapter != null && msg.arg2 > 0) {
                super@PageTag.onLoaded(c.mm.vwTagged?.items.isNullOrEmpty(), false)
                b.rv.adapter?.notifyItemRangeInserted(msg.arg1, msg.arg2)
            } else onLoaded(c.mm.vwTagged?.items.isNullOrEmpty())

            if (c.mm.vwTagged?.more_available == true && !b.rv.canScrollVertically(1)
                && thread?.active != true
            ) thread = FetchSome().also { it.start() }
        },
        HANDLE_ABORTED to { onFailed(c.getString(R.string.loadFailed)) },
        Api.HANDLE_ERROR to {
            onFailed(
                c.getString(
                    R.string.unknownError, (it.obj as NetworkResponse?)?.statusCode.toString()
                )
            )
        },
    )

    companion object : PageCompanion()

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageTagBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // List
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!b.rv.canScrollVertically(1) && thread?.active != true &&
                    c.mm.vwTagged?.more_available != false
                ) thread = FetchSome().also { it.start() }
            }
        })

        // Error
        b.error.setOnClickListener {
            c.b.refresher.isRefreshing = true
            thread = FetchSome().also { it.start() }
        }

        load()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload -> {
                if (tracker != null && c.mm.vwTagged?.items != null)
                    Saver(c, tracker!!.selection).start()
                tracker?.clearSelection()
            }
            R.id.vtSelectAll -> if (c.mm.vwTagged?.items != null)
                tracker?.setItemsSelected(c.mm.vwTagged!!.items!!.map { it.pk }, true)
            R.id.vtDeselectAll -> tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    fun load() {
        if (com.active.value != true) return
        if (c.mm.vwTagged != null)
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
        else if (thread?.active != true) {
            c.mm.vwTagged = null
            thread = FetchSome().also { it.start() }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
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
        override fun getKey(i: Int): String? = c.mm.vwTagged?.items?.getOrNull(i)?.pk
        override fun getPosition(key: String): Int {
            c.mm.vwTagged?.items?.forEachIndexed { i, med ->
                if (med.pk == key) return@getPosition i
            }
            return -1
        }
    }

    inner class FetchSome : BaseThread() {
        override fun run() {
            if (c.mm.vwUser == null) {
                c.mm.vwTagged = null
                interrupt()
                return; }
            c.reqQueue.adder = Api<Wrapper>(
                c, Api.Endpoint.TAGGED.url.format(
                    c.mm.vwUser?.id ?: "", c.mm.vwTagged?.next_max_id ?: ""
                ), Wrapper::class, handler, autoQueue = false, onError = { interrupt() }
            ) { wrapper ->
                if (c.mm.vwTagged == null) {
                    c.mm.vwTagged = wrapper
                    handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                } else c.mm.vwTagged?.apply {
                    val lastBefore = items?.size ?: 0
                    val ids = items?.map { it.pk }
                    wrapper.items
                        ?.let { if (ids != null) it.filter { p -> p.pk !in ids } else it }
                        ?.let { items?.addAll(it) }
                    next_max_id = wrapper.next_max_id
                    more_available = wrapper.more_available
                    handler?.obtainMessage(HANDLE_FETCHED, lastBefore, items?.size ?: 0)
                        ?.sendToTarget()
                }
                interrupt()
            }
        }
    }

    class Saver(c: Viewer, selection: Selection<String>) : SelectionHandler<Viewer>(c, selection) {
        companion object : Alive.OfThread()

        override val com: Alive.OfThread = Companion

        override fun handle() {
            val post = next()
            if (post == null) {
                Viewer.handler?.obtainMessage(PageSvd.HANDLE_INIT_QUEUER)?.sendToTarget()
                interrupt()
                return
            }
            (c as Viewer).mm.vwTagged?.items?.find { it.pk == post }?.queue(c.dao)
            ended()
        }
    }
}
