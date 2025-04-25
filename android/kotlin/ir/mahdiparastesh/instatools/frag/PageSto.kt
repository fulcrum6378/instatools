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

    /** Remember that c.vm.story can be queried and yet be null. */
    override fun isModelLoaded(): Boolean = c.vm.highlights != null
    override fun isModelEmpty(): Boolean = c.vm.story == null && c.vm.highlights?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListSto(c)
    override fun canLoadMore(): Boolean = !isModelLoaded()

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageStoBinding.inflate(inf, parent, false).let { b = it; it.root }

    override suspend fun fetch(reset: Boolean) {
        val uid = c.vm.user?.id() ?: return

        // load their story into the data model
        val pickle1 = Pickle(c.cacheDir, c.c.acc!!.id, Pickle.Type.STORY, c.vm.user!!.id!!)
        val cache1 = if (!reset) pickle1.restore<Story>() else null
        if (cache1 != null) // read from cache
            c.vm.story = cache1
        else {
            // fetch their story
            c.vm.story = Api.json<GraphQl>(
                Api.Endpoint.QUERY.url, true, GraphQlQuery.STORY.body(uid)
            ).data!!.xdt_api__v1__feed__reels_media!!.reels_media.firstOrNull()
            if (c.vm.story != null) pickle1.save(c.vm.story!!)
        }

        // load their highlights into the data model
        val pickle2 = Pickle(c.cacheDir, c.c.acc!!.id, Pickle.Type.HIGHLIGHTS, c.vm.user!!.id!!)
        val cache2 = if (!reset) pickle2.restore<Page<Story>>() else null
        if (cache2 != null) // read from cache
            c.vm.highlights = cache2
        else {
            // fetch their highlights
            c.vm.highlights = Api.json<GraphQl>(
                Api.Endpoint.QUERY.url, true, GraphQlQuery.PROFILE_HIGHLIGHTS_TRAY.body(uid),
            ).data!!.highlights!!
            pickle2.save(c.vm.highlights!!)
        }

        withContext(Dispatchers.Main) { onLoaded() }
    }

    override fun clear() {
        if (isBInitialised()) b.rv.adapter = createAdapter()
    }
}
