package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageRelBinding
import ir.mahdiparastesh.instatools.list.ListSto
import ir.mahdiparastesh.instatools.util.BasePageViewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageSto : BasePageViewer() {
    lateinit var b: PageRelBinding
    private var fetcher: Job? = null

    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageRelBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // error
        b.error.setOnClickListener {
            c.b.refresher.isRefreshing = true
            fetchAll()
        }

        if (c.mm.highlights != null)
            onLoaded(c.mm.story != null && c.mm.highlights?.edges.isNullOrEmpty())
        else fetchAll()
    }

    private fun fetchAll() {
        if (c.mm.user == null) {
            c.mm.story = null
            c.mm.highlights = null
            return; }
        if (fetcher != null) return

        fetcher = CoroutineScope(Dispatchers.IO).launch {
            val uid = c.mm.user!!.id()

            // load their story into the data model
            val pickle1 = Pickle(c.c, Pickle.Type.STORY, c.mm.user!!.id!!)
            val cache1 = pickle1.restore<Story>()
            if (cache1 != null) // read from cache
                c.mm.story = cache1
            else {
                // fetch their story
                val graphQl1 = Api.call<GraphQl>(
                    Api.Endpoint.QUERY.url, GraphQl::class,
                    isPost = true, body = GraphQlQuery.STORY.body(uid),
                    onError = { code -> onFailed(getString(Api.error(code), code)) }
                )
                if (graphQl1 == null) {
                    fetcher = null
                    return@launch; }
                c.mm.story =
                    graphQl1.data?.xdt_api__v1__feed__reels_media?.reels_media?.firstOrNull()
                c.mm.story?.also { pickle1.save(it) }
            }

            // load their highlights into the data model
            val pickle2 = Pickle(c.c, Pickle.Type.HIGHLIGHTS, c.mm.user!!.id!!)
            val cache2 = pickle2.restore<Page<Story>>()
            if (cache2 != null) // read from cache
                c.mm.highlights = cache2
            else {
                // fetch their highlights
                val graphQl2 = Api.call<GraphQl>(
                    Api.Endpoint.QUERY.url, GraphQl::class,
                    isPost = true, body = GraphQlQuery.PROFILE_HIGHLIGHTS_TRAY.body(uid),
                    onError = { code -> onFailed(getString(Api.error(code), code)) }
                )
                if (graphQl2 == null) {
                    fetcher = null
                    return@launch; }
                c.mm.highlights = graphQl2.data?.highlights
                c.mm.highlights?.also { pickle2.save(it) }
            }

            // update the UI
            withContext(Dispatchers.Main) {
                onLoaded(c.mm.story != null && c.mm.highlights?.edges.isNullOrEmpty())
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean) {
        super.onLoaded(isEmpty)
        b.rv.adapter = ListSto(c, this)
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

    override fun reset() {
        if (bInitialised) b.rv.adapter = ListSto(c, this)
    }
}
