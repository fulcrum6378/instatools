package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.selection.ItemKeyProvider
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
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageVwrBinding
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListVwr
import ir.mahdiparastesh.instatools.util.*
import ir.mahdiparastesh.instatools.util.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.GlideShimmer
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageVwr : BasePageViewer() {
    lateinit var b: PageVwrBinding

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = null
    override val jumper: ImageView? get() = b.jumper

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun shouldLoadOnPrepare(): Boolean = c.mm.user != null || c.mm.profile != null
    override fun isModelLoaded(): Boolean = c.mm.posts != null
    override fun isModelEmpty(): Boolean = c.mm.posts?.edges?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListVwr(c, this)
    override fun canLoadMore(): Boolean = c.mm.posts?.page_info?.has_next_page != false

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageVwrBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // list
        b.rv.setHasFixedSize(true)
        Delay(1500) { b.rv.layoutParams = b.rv.layoutParams.apply { height = b.nsv.height } }

        // profile
        b.proPic.layoutParams = b.proPic.layoutParams
            .apply { height = screenHeight() }
        b.proClick.setOnClickListener { v ->
            val picture = c.mm.user?.originalPicture() ?: return@setOnClickListener
            MaterialMenu(c, v, R.menu.viewer_pic_more,
                R.id.vpDownload to {
                    CoroutineScope(Dispatchers.IO).launch {
                        c.c.downloads.add<Download>(
                            Download(
                                Utils.PROFILE_PHOTO,
                                Utils.now(),
                                picture,
                                0x1,
                                c.mm.user!!.username!!,
                                c.mm.user!!.biography,
                                Utils.PROFILE.format(c.mm.user!!.username!!),
                                c.mm.user!!.profile_pic_url,
                                null, null, null
                            ), true
                        )
                        Downloads.initService(c)
                    }
                }
            ).show()
        }

        // buttons for other pages
        b.toPageRel.setOnClickListener { if (isModelLoaded()) c.turnToPage(0) }
        b.toPageTag.setOnClickListener { if (isModelLoaded()) c.turnToPage(2) }

        showProfile()
    }

    override fun setOnScrollListener() {
        b.nsv.setOnScrollChangeListener { v, _, _, _, _ -> onScroll() }
    }

    override fun onScroll() {
        super.onScroll()
        b.rv.isNestedScrollingEnabled = hasReachedBottom()
    }

    override fun hasReachedBottom(): Boolean =
        !b.nsv.canScrollVertically(1)

    override fun updateShadow() {
        if (isBInitialised()) c.b.tbShadow.vish(b.nsv.scrollY > 0)
    }

    @SuppressLint("RestrictedApi")
    override fun shouldShowJumper(): Boolean =
        b.nsv.computeVerticalScrollOffset() > screenHeight() && expandable?.zoomed != true

    override fun canRefresh(): Boolean =
        super.canRefresh() && !b.nsv.canScrollVertically(-1)

    fun showProfile(reset: Boolean = false) {
        if (c.mm.user == null || c.mm.profile == null || !isBInitialised()) return

        // profile picture
        Glide.with(c.c)
            .load(c.mm.profile!!.profile_pic_url_hd!!)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .addListener(GlideShimmer(b.proPic, b.proPicIv))
            .into(b.proPicIv)

        // followers & following
        b.followersNum.text = c.mm.profile!!.edge_followed_by.toString()
        b.followingNum.text = c.mm.profile!!.edge_follow.toString()

        // is the page private and not followed?
        val showPv = c.mm.user?.pv() == true && c.mm.profile?.followed_by_viewer == false
            && c.mm.user?.username != c.c.acc?.user
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
                    val vPad = ((c.c.dm.heightPixels.toFloat()
                        - c.c.dm.widthPixels.toFloat()) * 0.19f).toInt()
                    topMargin = vPad
                    bottomMargin = vPad
                }
        } else
            load(reset)

        // update Favourite
        c.mm.fav?.also {
            CoroutineScope(Dispatchers.IO).launch {
                c.c.addFavourite(it)
            }
        }
    }

    override suspend fun fetch(reset: Boolean) {
        // first read from cache if available
        val pickle = Pickle(c.cacheDir, c.c.acc!!.id, Pickle.Type.POSTS, c.mm.user!!.id!!)
        val cache = if (c.mm.posts == null && !reset) pickle.restore<Page<Media>>() else null
        if (cache != null) {
            c.mm.posts = cache
            withContext(Dispatchers.Main) { onLoaded() }
            return; }

        // fetch online posts
        val cursor = if (!reset) c.mm.posts?.edges?.lastOrNull()?.node?.id().toString() else "null"
        val page = Api.json<GraphQl>(
            Api.Endpoint.QUERY.url,
            true, GraphQlQuery.PROFILE_POSTS.body(c.mm.user!!.username!!, "33", cursor)
        ).data!!.xdt_api__v1__feed__user_timeline_graphql_connection!!

        // update the data model and the UI
        if (c.mm.posts == null || reset) {
            c.mm.posts = page
            withContext(Dispatchers.Main) { onLoaded() }
        } else c.mm.posts?.apply {
            val lastBefore = edges.size
            edges.addAll(page.edges)
            page_info.has_next_page = page.page_info.has_next_page
            withContext(Dispatchers.Main) { onLazilyLoaded(lastBefore, page.edges.size) }
        }

        // cache the data model
        c.mm.posts?.also { pickle.save(it) }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload -> processMedia()
            R.id.vtSelectAll -> if (c.mm.posts?.edges != null)
                tracker?.setItemsSelected(c.mm.posts!!.edges.map { it.node.id() }, true)
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

    fun processMedia(download: Boolean = true) {
        val selection = tracker?.selection ?: return
        val posts = c.mm.posts ?: return
        CoroutineScope(Dispatchers.Default).launch {
            for (edg in posts.edges.indices) {
                if (posts.edges[edg].node.id() !in selection) continue
                if (download) c.c.downloads.addAll<Download>(posts.edges[edg].node.queue(), false)
            }
            if (download) {
                c.c.downloads.save<Download>()
                Downloads.initService(c)
            }
            withContext(Dispatchers.Main) {
                tracker?.clearSelection()
            }
        }
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.mm.posts?.edges?.getOrNull(i)?.node?.id()
        override fun getPosition(key: String): Int {
            c.mm.posts?.edges?.forEachIndexed { i, edge ->
                if (edge.node.id() == key) return@getPosition i
            }
            return -1
        }
    }
}
