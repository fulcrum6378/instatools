package ir.mahdiparastesh.instatools.frag

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.FeedTray
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.base.BaseActivity
import ir.mahdiparastesh.instatools.base.BasePageMain
import ir.mahdiparastesh.instatools.base.OnlineLister
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageTryBinding
import ir.mahdiparastesh.instatools.list.ListTry
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

class PageTry : BasePageMain(BaseActivity.Theme.TERTIARY), OnlineLister {
    private lateinit var b: PageTryBinding
    val pickle: Pickle by lazy {
        Pickle(c.cacheDir, c.c.acc!!.id, Pickle.Type.TRAY, null)
    }

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override var job: Job? = null
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val expandable: Expandable by lazy {
        Expandable(
            c, b.expanded, c.color(if (!c.night) R.color.defBG else R.color.CT),
            ContextThemeWrapper(c, R.style.Theme_InstaTools_Tertiary)
        ) {
            updateShadow()
            updateJumper()
        }
    }
    override val selectiveMenuRes: Int? = null

    override fun onSelectionStarted() {}
    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.vm.tray != null
    override fun isModelEmpty(): Boolean = c.vm.tray?.tray?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListTry(c, this)
    override fun canLoadMore(): Boolean = !isModelLoaded()

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageTryBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override suspend fun fetch(reset: Boolean) {

        // first read from cache if available
        val cache = if (c.vm.tray == null && !reset) pickle.restore<FeedTray>() else null
        if (cache != null) {
            c.vm.tray = cache
            withContext(Dispatchers.Main) { onLoaded() }
            return; }

        // fetch an online tray and update the data model
        c.vm.tray = Api.json<GraphQl>(Api.Endpoint.QUERY.url, true, GraphQlQuery.FEED_TRAY.body())
            .data!!.xdt_api__v1__feed__reels_tray
        //c.vm.tray?.tray?.sortBy { it.seen_ranked_position!! }

        // update the UI
        withContext(Dispatchers.Main) { onLoaded() }

        // cache the data model
        c.vm.tray?.also { pickle.save(it) }
    }

    /*override fun onLoaded() {
        super<OnlineLister>.onLoaded()
        c.vm.trayCount.value = c.vm.tray?.tray?.size
    }*/

    override fun goBack(): Boolean {
        if (expandable.zoomed) {
            b.jumper.vis(true)
            expandable.collapse()
            return true
        }
        return super.goBack()
    }
}
