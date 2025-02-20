package ir.mahdiparastesh.instatools.frag

import android.os.Build
import android.os.Bundle
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.PageVwrBinding
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListVwr
import ir.mahdiparastesh.instatools.util.*
import ir.mahdiparastesh.instatools.util.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.GlideShimmer
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.SelectionHandler
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageVwr : BasePageViewer() {
    lateinit var b: PageVwrBinding
    override val root: ConstraintLayout? get() = if (isBInitialised()) b.root else null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun shouldLoadOnPrepare(): Boolean = c.mm.user != null || c.mm.profile != null
    override fun isModelLoaded(): Boolean = c.mm.posts != null
    override fun isModelEmpty(): Boolean = c.mm.posts?.edges?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListVwr(c, this)
    override fun screenHeight(): Int = c.dm.heightPixels
    override fun canLoadMore(): Boolean = c.mm.posts?.page_info?.has_next_page != false

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageVwrBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // list
        b.rv.setHasFixedSize(true)
        Delay(1500) { b.rv.layoutParams = b.rv.layoutParams.apply { height = b.nsv.height } }

        // profile
        b.proPic.layoutParams = b.proPic.layoutParams.apply {
            height = c.dm.widthPixels
        }
        b.proClick.setOnClickListener { v ->
            val picture = c.mm.user?.picture() ?: return@setOnClickListener
            MaterialMenu(c, v, R.menu.viewer_pic_more,
                R.id.vpDownload to {
                    CoroutineScope(Dispatchers.IO).launch {
                        c.dao.addQueued(
                            Queued(
                                Persistent.now(),
                                UiTools.PROFILE.format(c.mm.user!!.username!!),
                                Persistent.now(),
                                c.mm.user!!.id!!,
                                c.mm.user!!.username!!,
                                "profile_photo",
                                picture,
                                c.mm.user!!.profile_pic_url,
                                0x1,
                                null,
                                c.mm.user!!.biography
                            )
                        )
                        Downloads.initService(c)
                    }
                }
            ).show()
        }

        // buttons for other pages
        b.toPageRel.setOnClickListener { c.turnToPage(0) }
        b.toPageTag.setOnClickListener { c.turnToPage(2) }

        showProfile()
    }

    override fun setOnScrollListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            b.nsv.setOnScrollChangeListener { v, _, _, _, _ -> onScroll() }
        else
            b.nsv.viewTreeObserver.addOnScrollChangedListener { onScroll() }
    }

    override fun onScroll() {
        b.rv.isNestedScrollingEnabled = !b.nsv.canScrollVertically(1)
    }

    override fun updateShadow() {
        if (isBInitialised()) c.b.tbShadow.vish(b.nsv.scrollY > 0)
    }

    override fun canRefresh(): Boolean =
        super.canRefresh() && !b.nsv.canScrollVertically(-1)

    fun showProfile() {
        if (c.mm.user == null || c.mm.profile == null || !isBInitialised()) return

        // profile picture
        Glide.with(c.c)
            .load(c.mm.user!!.picture())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .addListener(GlideShimmer(b.proPic, b.proPicIv))
            .into(b.proPicIv)

        // followers & following
        b.followersNum.text = c.mm.profile!!.edge_followed_by.toString()
        b.followingNum.text = c.mm.profile!!.edge_follow.toString()

        // is the page private and not followed?
        val showPv = c.mm.user?.pv() == true && c.mm.profile?.followed_by_viewer == false
            && c.mm.user?.username != c.m.acc?.user
        b.privateAcc.vis(showPv)
        b.rv.vis(!showPv)
        if (showPv) {
            b.privateAcc.setCompoundDrawablesWithIntrinsicBounds(
                null, c.drawable(
                    R.drawable.private_account, if (c.night()) R.color.defCA else null
                )!!, null, null
            )
            b.privateAcc.layoutParams =
                (b.privateAcc.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    val vPad = ((c.dm.heightPixels.toFloat()
                        - c.dm.widthPixels.toFloat()) * 0.19f).toInt()
                    topMargin = vPad
                    bottomMargin = vPad
                }
        } else
            load()

        // update Favourite
        c.mm.fav?.apply {
            var changed = false
            if (user != c.mm.user!!.username) {
                user = c.mm.user!!.username!!
                changed = true
            }
            if (name != c.mm.user!!.full_name) {
                name = c.mm.user!!.full_name!!
                changed = true
            }
            if (photo != c.mm.user!!.profile_pic_url_hd) {
                photo = c.mm.user!!.profile_pic_url_hd
                changed = true
            }
            if (changed) CoroutineScope(Dispatchers.IO).launch { c.dao.updateFavourite(this@apply) }
        }
    }

    override suspend fun fetch(reset: Boolean) {
        // first read from cache if available
        val pickle = Pickle(c.c, Pickle.Type.POSTS, c.mm.user!!.id!!)
        val cache = if (c.mm.posts == null && !reset) pickle.restore<Page<Media>>() else null
        if (cache != null) {
            c.mm.posts = cache
            withContext(Dispatchers.Main) { onLoaded() }
            return; }

        // fetch online posts
        val graphQl = Api.call<GraphQl>(
            Api.Endpoint.QUERY.url, GraphQl::class,
            isPost = true, body = GraphQlQuery.PROFILE_POSTS.body(
                c.mm.user!!.username!!, "33", c.mm.posts?.edges?.lastOrNull()?.node?.pk().toString()
            ), onError = { code -> onFailed(code) }
        )
        if (graphQl == null) return
        val page = graphQl.data?.xdt_api__v1__feed__user_timeline_graphql_connection
        if (page == null) {
            withContext(Dispatchers.Main) { onLazilyFailed(-3) }
            return; }

        // update the data model and the UI
        if (c.mm.posts == null || reset) {
            c.mm.posts = page
            withContext(Dispatchers.Main) { onLoaded() }
        } else c.mm.posts?.apply {
            val lastBefore = edges.size
            edges.addAll(page.edges)
            withContext(Dispatchers.Main) { onLazilyLoaded(lastBefore, page.edges.size) }
            page_info.has_next_page = page.page_info.has_next_page
        }

        // cache the data model
        c.mm.posts?.also { pickle.save(it) }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload -> {
                if (tracker != null && c.mm.posts?.edges != null)
                    Saver(tracker!!.selection)
                tracker?.clearSelection()
            }
            R.id.vtSelectAll -> if (c.mm.posts?.edges != null)
                tracker?.setItemsSelected(c.mm.posts!!.edges.map { it.node.pk() }, true)
            R.id.vtDeselectAll -> tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    override fun onRefresh() {
        c.load(reset = true)
    }

    override fun buildSelection() {
        tracker = SelectionTracker.Builder(
            "viewer_main", b.rv,
            PostKeyProvider(), ListPost.PostDetailsLookup(b.rv),
            StorageStrategy.createStringStorage()
        ).build().also { it.addObserver(SelectObserver()) }
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.mm.posts?.edges?.getOrNull(i)?.node?.pk()
        override fun getPosition(key: String): Int {
            c.mm.posts?.edges?.forEachIndexed { i, edge ->
                if (edge.node.pk() == key) return@getPosition i
            }
            return -1
        }
    }

    inner class Saver(selection: Selection<String>) : SelectionHandler(selection) {
        override suspend fun handle() {
            val edg = next()
            if (edg == null) {
                Downloads.initService(c)
                return; }
            c.mm.posts?.edges?.find { it.node.pk() == edg }
                ?.also { edge -> edge.node.queue(c.dao) } // TODO handle all posts in one search
            ended()
        }
    }
}
