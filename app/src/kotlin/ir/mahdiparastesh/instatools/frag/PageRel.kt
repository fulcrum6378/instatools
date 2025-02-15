package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import ir.mahdiparastesh.instatools.databinding.PageRelBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.json.Rest.Highlights
import ir.mahdiparastesh.instatools.json.Rest.Story
import ir.mahdiparastesh.instatools.list.ListRel
import ir.mahdiparastesh.instatools.more.BasePageViewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

class PageRel : BasePageViewer() {
    lateinit var b: PageRelBinding
    private var thread: Job? = null
    private var storyFetched = false
    private var highlightsFetched = false

    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageRelBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        load()

        // Error
        b.error.setOnClickListener {
            c.b.refresher.isRefreshing = true
            fetchAll()
        }
    }

    fun load() {
        if (c.mm.vwReels != null)
            onLoaded(c.mm.vwReels.isNullOrEmpty())
        else fetchAll()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean) {
        super.onLoaded(isEmpty)
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

    private fun fetchAll() {
        if (c.mm.vwUser == null) {
            c.mm.vwReels = null
            return; }
        if (thread != null) {
            return; }

        thread = CoroutineScope(Dispatchers.IO).launch {
            c.mm.vwReels = CopyOnWriteArrayList()
            val story = Api.call<Story>(
                Api.Endpoint.STORY.url.format(c.mm.vwUser?.id ?: ""), Story::class,
                onError = { code -> onFailed(Api.error(code)) }
            )
            if (story == null) return@launch
            if (!story.reel?.items.isNullOrEmpty()) c.mm.vwReels?.add(story.reel)
            storyFetched = true

            val highlights = Api.call<Highlights>(
                Api.Endpoint.HIGHLIGHTS.url.format(c.mm.vwUser?.id ?: ""), Highlights::class,
                onError = { code -> onFailed(Api.error(code)) }
            )
            if (highlights == null) return@launch
            c.mm.vwReels?.addAll(highlights.tray)
            highlightsFetched = true

            try {
                c.mm.vwReels?.sortByDescending { it is Rest.StoryReel }
            } catch (_: java.lang.UnsupportedOperationException) {
                // Mysterious error by CopyOnWriteArrayList$COWIterator.set  while sorting
            }
            withContext(Dispatchers.Main) {
                onLoaded(c.mm.vwReels.isNullOrEmpty())
            }
        }
    }
}
