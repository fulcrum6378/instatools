package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.volley.NetworkResponse
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.PageRelBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.json.Rest.Highlights
import ir.mahdiparastesh.instatools.json.Rest.Story
import ir.mahdiparastesh.instatools.list.ListRel
import ir.mahdiparastesh.instatools.more.BasePageViewer
import ir.mahdiparastesh.instatools.more.BaseThread
import java.util.concurrent.CopyOnWriteArrayList

class PageRel : BasePageViewer() {
    lateinit var b: PageRelBinding
    private var thread: FetchAll? = null

    override val com: PageCompanion = Companion
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout get() = b.root
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_FETCHED to { onLoaded(c.m.vwReels.isNullOrEmpty()) },
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
        PageRelBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        load()

        // Error
        b.error.setOnClickListener {
            c.b.refresher.isRefreshing = true
            thread = FetchAll().also { it.start() }
        }
    }

    fun load() {
        if (com.active.value != true) return
        if (c.m.vwReels != null)
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
        else if (thread?.active != true)
            thread = FetchAll().also { it.start() }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
        if (b.rv.adapter == null) b.rv.adapter = ListRel(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
        if (tracker == null) buildSelection()
        c.b.refresher.isRefreshing = false
    }

    override fun onFailed(message: String) {
        super.onFailed(message)
        c.b.refresher.isRefreshing = false
    }

    override fun buildSelection() {
    }

    override fun onRecyclerViewScrolled() {
        super.onRecyclerViewScrolled()
        updateShadow()
    }

    inner class FetchAll : BaseThread() {
        private var storyFetched = false
        private var highlightsFetched = false

        override fun run() {
            if (c.m.vwUser == null) {
                c.m.vwReels = null
                interrupt()
                return; }
            c.m.vwReels = CopyOnWriteArrayList()
            c.reqQueue.adder = Api<Story>(
                c, Api.Endpoint.STORY.url.format(c.m.vwUser?.id ?: ""), Story::class,
                handler, autoQueue = false, onError = { interrupt() }) { story ->
                if (!story.reel?.items.isNullOrEmpty()) c.m.vwReels?.add(story.reel)
                storyFetched = true
                interrupt()
            }
            c.reqQueue.adder = Api<Highlights>(
                c, Api.Endpoint.HIGHLIGHTS.url.format(c.m.vwUser?.id ?: ""), Highlights::class,
                handler, autoQueue = false, onError = { interrupt() }) { highlights ->
                c.m.vwReels?.addAll(highlights.tray)
                highlightsFetched = true
                interrupt()
            }
        }

        override fun interrupt() {
            if (storyFetched && highlightsFetched) {
                try {
                    c.m.vwReels?.sortByDescending { it is Rest.StoryReel }
                } catch (e: java.lang.UnsupportedOperationException) {
                    // by CollectionsKt__MutableCollectionsJVMKt.sortWith()
                    // by Collections.sort()
                    // by CopyOnWriteArrayList$COWIterator.set() extended from ListIterator.set()
                    // if the set operation is not supported by this list iterator
                    if (BuildConfig.DEBUG) throw e // TODO analyse this
                }
                handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                super.interrupt()
            }
        }
    }
}
