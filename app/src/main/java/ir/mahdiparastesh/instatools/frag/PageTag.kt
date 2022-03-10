package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
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
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.PageTagBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media.MediaWrapperApi
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListTag
import ir.mahdiparastesh.instatools.more.BasePageViewer
import ir.mahdiparastesh.instatools.more.BaseSaver
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.more.Persistent

class PageTag(c: Viewer) : BasePageViewer(c) {
    private lateinit var b: PageTagBinding
    private var thread: FetchSome? = null

    override val com: PageCompanion = Companion
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout get() = b.root
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_FETCHED to { msg ->
            if (b.rv.adapter != null && msg.arg2 > 0) {
                super@PageTag.onLoaded(c.m.vwTagged?.items.isNullOrEmpty(), false)
                b.rv.adapter?.notifyItemRangeInserted(msg.arg1, msg.arg2)
            } else onLoaded(c.m.vwTagged?.items.isNullOrEmpty())

            if (c.m.vwTagged?.more_available == true && !b.rv.canScrollVertically(1)
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

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageTagBinding.inflate(inf, parent, false)
        essentials()

        // List
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!b.rv.canScrollVertically(1) && thread?.active != true &&
                    c.m.vwTagged?.more_available != false
                ) thread = FetchSome().also { it.start() }
            }
        })
        fetch()

        return b.root
    }

    fun fetch() {
        thread = FetchSome().also { it.start() }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)

        if (b.rv.adapter == null) b.rv.adapter = ListTag(c, this)
        else b.rv.adapter?.notifyDataSetChanged()

        if (tracker != null) return
        tracker = SelectionTracker.Builder(
            "viewer_tagged", b.rv,
            PostKeyProvider(), ListPost.PostDetailsLookup(b.rv),
            StorageStrategy.createStringStorage()
        ).build().also { it.addObserver(SelectObserver()) }
    }

    override fun onRecyclerViewScrolled() {
        updateShadow()
        updateJumper()
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.m.vwTagged?.items?.getOrNull(i)?.pk
        override fun getPosition(key: String): Int {
            c.m.vwTagged?.items?.forEachIndexed { i, med ->
                if (med.pk == key) return@getPosition i
            }
            return -1
        }
    }

    inner class FetchSome : BaseThread() {
        override fun run() {
            if (c.m.vwUser == null) {
                c.m.vwTagged = null
                interrupt()
                return; }
            Api<MediaWrapperApi>(
                c, Api.Type.TAGGED.url.format(c.m.vwUser!!.id, c.m.vwTagged?.next_max_id ?: ""),
                MediaWrapperApi::class, handler, onError = { interrupt() }
            ) { wrapper ->
                if (c.m.vwTagged == null) {
                    c.m.vwTagged = wrapper
                    handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                } else c.m.vwTagged?.apply {
                    val lastBefore = items?.size ?: 0
                    wrapper.items?.let { items?.addAll(it) }
                    next_max_id = wrapper.next_max_id
                    more_available = wrapper.more_available
                    handler?.obtainMessage(HANDLE_FETCHED, lastBefore, items?.size ?: 0)
                        ?.sendToTarget()
                }
                interrupt()
            }
        }
    }

    inner class Saver(selection: Selection<String>) : BaseSaver(selection) {// TODO: CUSTOMISE IT
        override fun handle() {
            val svd = list.getOrNull(0)
            if (svd == null) {
                Viewer.handler?.obtainMessage(PageSvd.HANDLE_INIT_QUEUER)?.sendToTarget()
                interrupt()
                return
            }
            c.m.vwUser?.edges()?.find { it.node.id == svd }?.let { edge ->
                c.dao.addQueued(
                    Queued(Persistent.now(), Api.Type.POST_ITEM.url.format(edge.node.shortcode))
                )
            }
            ended()
        }
    }
}
