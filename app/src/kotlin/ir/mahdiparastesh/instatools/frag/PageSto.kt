package ir.mahdiparastesh.instatools.frag

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageStoBinding
import ir.mahdiparastesh.instatools.list.ListSto
import ir.mahdiparastesh.instatools.util.BasePageViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PageSto : BasePageViewer() {
    lateinit var b: PageStoBinding
    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.mm.story != null && c.mm.highlights != null
    override fun isModelEmpty(): Boolean = c.mm.story == null && c.mm.highlights?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListSto(c, this)
    override fun canLoadMore(): Boolean = !isModelLoaded()

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageStoBinding.inflate(inf, parent, false).let { b = it; it.root }

    override suspend fun fetch(reset: Boolean) {
        val uid = c.mm.user!!.id()

        // load their story into the data model
        val pickle1 = Pickle(c.c, Pickle.Type.STORY, c.mm.user!!.id!!)
        val cache1 = if (!reset) pickle1.restore<Story>() else null
        if (cache1 != null) // read from cache
            c.mm.story = cache1
        else {
            // fetch their story
            val graphQl1 = Api.call<GraphQl>(
                Api.Endpoint.QUERY.url, GraphQl::class,
                isPost = true, body = GraphQlQuery.STORY.body(uid),
                onError = { code -> onFailed(code) }
            )
            if (graphQl1 == null) return
            c.mm.story =
                graphQl1.data?.xdt_api__v1__feed__reels_media?.reels_media?.firstOrNull()
            c.mm.story?.also { pickle1.save(it) }
        }

        // load their highlights into the data model
        val pickle2 = Pickle(c.c, Pickle.Type.HIGHLIGHTS, c.mm.user!!.id!!)
        val cache2 = if (!reset) pickle2.restore<Page<Story>>() else null
        if (cache2 != null) // read from cache
            c.mm.highlights = cache2
        else {
            // fetch their highlights
            val graphQl2 = Api.call<GraphQl>(
                Api.Endpoint.QUERY.url, GraphQl::class,
                isPost = true, body = GraphQlQuery.PROFILE_HIGHLIGHTS_TRAY.body(uid),
                onError = { code -> onFailed(code) }
            )
            if (graphQl2 == null) return
            c.mm.highlights = graphQl2.data?.highlights
            c.mm.highlights?.also { pickle2.save(it) }
        }

        withContext(Dispatchers.Main) { onLoaded() }
    }

    override fun buildSelection() {
    }

    override fun clear() {
        if (isBInitialised()) b.rv.adapter = createAdapter()
    }
}
