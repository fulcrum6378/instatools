package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.PageVwrBinding
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListVwr
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.GlideShimmer
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageVwr : BasePageViewer() {
    private lateinit var b: PageVwrBinding
    private var thread: Job? = null
    val proPicIv: ImageView? get() = if (bInitialised) b.proPicIv else null
    val privateAcc: AppCompatTextView? get() = if (bInitialised) b.privateAcc else null

    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageVwrBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // List
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            b.nsv.setOnScrollChangeListener { v, _, _, _, _ ->
                updateShadow()
                b.rv.isNestedScrollingEnabled = !v.canScrollVertically(1)
            }
        else b.nsv.viewTreeObserver.addOnScrollChangedListener {
            updateShadow()
            b.rv.isNestedScrollingEnabled = !b.nsv.canScrollVertically(1)
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!b.rv.canScrollVertically(1)) fetchSome()
            }
        })
        c.b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback b.nsv.canScrollVertically(-1)
        }
        b.rv.setHasFixedSize(true)
        Delay(1500) { b.rv.layoutParams = b.rv.layoutParams.apply { height = b.nsv.height } }

        // Profile
        b.proPic.layoutParams = b.proPic.layoutParams.apply {
            height = c.dm.widthPixels
        }
        b.proClick.setOnClickListener { v ->
            val picture = c.mm.vwUser?.picture() ?: return@setOnClickListener
            MaterialMenu(c, v, R.menu.viewer_pic_more,
                R.id.vpDownload to {
                    CoroutineScope(Dispatchers.IO).launch {
                        c.dao.addQueued(
                            Queued(
                                Persistent.now(),
                                UiTools.PROFILE.format(c.user!!),
                                Persistent.now(),
                                c.mm.vwUser!!.id(),
                                c.user!!,
                                "profile_photo",
                                picture,
                                c.mm.vwUser!!.profile_pic_url,
                                0x1,
                                null,
                                c.mm.vwUser!!.biography
                            )
                        )
                        withContext(Dispatchers.Main) { Downloads.initService(c, "") }
                    }
                }
            ).show()
        }
        showProfile()

        // Pagination
        b.toPageRel.setOnClickListener { c.turnToPage(0) }
        b.toPageTag.setOnClickListener { c.turnToPage(2) }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload -> {
                if (tracker != null && c.mm.vwUser?.edges() != null)
                    Saver(tracker!!.selection)
                tracker?.clearSelection()
            }
            R.id.vtSelectAll -> if (c.mm.vwUser?.edges() != null)
                tracker?.setItemsSelected(c.mm.vwUser!!.edges()!!.map { it.node.id }, true)
            R.id.vtDeselectAll -> tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    fun showProfile() {
        if (c.mm.vwUser == null || !bInitialised) return
        Glide.with(c.c)
            .load(c.mm.vwUser!!.hdPhoto())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .addListener(GlideShimmer(b.proPic, b.proPicIv))
            .into(b.proPicIv)
        b.followersNum.text = c.mm.vwUser!!.edge_followed_by.toString()
        b.followingNum.text = c.mm.vwUser!!.edge_follow.toString()
        fetched()

        if (c.mm.vwUser != null) c.dbFav?.apply {
            var changed = false
            if (user != c.mm.vwUser!!.username) {
                user = c.mm.vwUser!!.username
                changed = true
            }
            if (name != c.mm.vwUser!!.full_name) {
                name = c.mm.vwUser!!.full_name
                changed = true
            }
            if (photo != c.mm.vwUser!!.profile_pic_url_hd) {
                photo = c.mm.vwUser!!.profile_pic_url_hd
                changed = true
            }
            if (changed) CoroutineScope(Dispatchers.IO).launch { c.dao.updateFavourite(this@apply) }
        }
    }

    private fun fetchSome() {
        if (thread != null || c.mm.vwUser == null || c.mm.vwUser?.hasMore() == false)
            return
        thread = CoroutineScope(Dispatchers.IO).launch {
            val res = Api.call<GraphQl>(
                Api.Endpoint.POSTS.url.format(
                    c.mm.vwUser!!.id,
                    c.mm.vwUser!!.edge_owner_to_timeline_media!!.page_info.end_cursor
                ), GraphQl::class, onError = { code ->
                    UiTools.snackbar(b.root, Api.error(code), Snackbar.LENGTH_LONG)
                }
            )
            if (res == null) {
                thread = null
                return@launch; }

            val add = res.data?.user?.edge_owner_to_timeline_media
            if (add == null) {
                withContext(Dispatchers.Main) {
                    UiTools.snackbar(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG)
                }
                thread = null
                return@launch; }

            c.mm.vwUser?.edge_owner_to_timeline_media?.apply {
                page_info = add.page_info
                count = add.count
                val lastBefore = edges.size
                val ids = edges.map { it.node.id }
                edges.addAll(add.edges.filter { it.node.id !in ids })
                withContext(Dispatchers.Main) {
                    fetched(lastBefore, add.edges.size)
                }
            }
            thread = null
        }
    }

    private fun fetched(arg1: Int = 0, arg2: Int = 0) {
        if (b.rv.adapter != null && arg2 > 0)
            b.rv.adapter?.notifyItemRangeInserted(arg1, arg2)
        onLoaded(c.mm.vwUser?.edges().isNullOrEmpty())

        if (!b.rv.canScrollVertically(1)) fetchSome()

        val showPv = c.mm.vwUser?.pv() == true && c.mm.vwUser?.followed_by_viewer == false
            && c.mm.vwUser?.username != c.m.acc?.user
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
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean) {
        super.onLoaded(isEmpty)
        if (b.rv.adapter == null) b.rv.adapter = ListVwr(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
        if (tracker == null) buildSelection()
    }

    override fun buildSelection() {
        tracker = SelectionTracker.Builder(
            "viewer_main", b.rv,
            PostKeyProvider(), ListPost.PostDetailsLookup(b.rv),
            StorageStrategy.createStringStorage()
        ).build().also { it.addObserver(SelectObserver()) }
    }

    override fun avoidRefresh(): Boolean = if (::b.isInitialized)
        b.nsv.canScrollVertically(-1) || tracker?.hasSelection() == true
    else false

    override fun updateShadow() {
        if (bInitialised) c.b.tbShadow.vish(b.nsv.scrollY > 0)
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.mm.vwUser?.edges()?.getOrNull(i)?.node?.id
        override fun getPosition(key: String): Int {
            c.mm.vwUser?.edges()?.forEachIndexed { i, edge ->
                if (edge.node.id == key) return@getPosition i
            }
            return -1
        }
    }

    inner class Saver(selection: Selection<String>) : SelectionHandler(selection) {

        override suspend fun handle() {
            val edg = next()
            if (edg == null) {
                Downloads.initService(c)
                return
            }
            c.mm.vwUser?.edges()?.find { it.node.id == edg }?.let { edge ->
                c.dao.addQueued(
                    Queued(Persistent.now(), UiTools.POST_LINK.format(edge.node.shortcode))
                )
            }
            ended()
        }
    }
}
