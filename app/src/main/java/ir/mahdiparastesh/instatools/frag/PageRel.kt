package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.volley.NetworkResponse
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.PageRelBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.json.Rest.Highlights
import ir.mahdiparastesh.instatools.json.Rest.Story
import ir.mahdiparastesh.instatools.list.ListRel
import ir.mahdiparastesh.instatools.more.BasePageViewer
import ir.mahdiparastesh.instatools.more.BaseThread
import java.util.concurrent.CopyOnWriteArrayList

class PageRel : BasePageViewer() {
    private lateinit var b: PageRelBinding
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

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageRelBinding.inflate(inf, parent, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetch()
    }

    fun fetch() {
        if (com.active.value != true || thread?.active == true) return
        thread = FetchAll().also { it.start() }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
        if (b.rv.adapter == null) b.rv.adapter = ListRel(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
        if (tracker == null) buildSelection()
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
            Api<Story>(
                c, Api.Type.STORY.url.format(c.m.vwUser?.id ?: ""), Story::class, handler,
                onError = { interrupt() }) { story ->
                c.m.vwReels?.add(story.reel)
                storyFetched = true
                interrupt()
            }
            Api<Highlights>(
                c, Api.Type.HIGHLIGHTS.url.format(c.m.vwUser?.id ?: ""), Highlights::class,
                handler, onError = { interrupt() }) { highlights ->
                c.m.vwReels?.addAll(highlights.tray)
                highlightsFetched = true
                interrupt()
            }
        }

        override fun interrupt() {
            if (storyFetched && highlightsFetched) {
                c.m.vwReels?.sortByDescending { it is Rest.StoryReel }
                handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                super.interrupt()
            }
        }
    }

    /*inner class Saver(selection: Selection<String>) : BaseSaver(selection) {
        override fun handle() {
            val rel = list.getOrNull(0)
            if (rel == null) {
                Viewer.handler?.obtainMessage(PageSvd.HANDLE_INIT_QUEUER)?.sendToTarget()
                interrupt()
                return
            }
            c.m.vwUser?.edges()?.find { it.node.id == rel }?.let { edge ->
                c.dao.addQueued(
                    Queued(Persistent.now(), Api.Type.POST_ITEM.url.format(edge.node.shortcode))
                )
            }
            ended()
        }
    }*/
}
