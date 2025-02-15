package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Message
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
import com.android.volley.NetworkResponse
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.PageVwrBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.GraphQl
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListVwr
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.GlideShimmer
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageVwr : BasePageViewer() {
    private lateinit var b: PageVwrBinding
    private var thread: FetchSome? = null
    val proPicIv: ImageView? get() = if (bInitialised) b.proPicIv else null
    val privateAcc: AppCompatTextView? get() = if (bInitialised) b.privateAcc else null

    override val com: PageCompanion = Companion
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_FETCHED to { msg ->
            if (b.rv.adapter != null && msg.arg2 > 0) {
                super.onLoaded(c.mm.vwUser?.edges().isNullOrEmpty())
                b.rv.adapter?.notifyItemRangeInserted(msg.arg1, msg.arg2)
            } else onLoaded(c.mm.vwUser?.edges().isNullOrEmpty())

            if (c.mm.vwUser?.hasMore() == true && thread?.active != true
                && !b.rv.canScrollVertically(1)
            ) thread = FetchSome().also { it.start() }

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
        },
        HANDLE_ABORTED to {
            UiTools.snackbar(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG)
        },
        Api.HANDLE_ERROR to {
            UiTools.snackbar(
                b.root, c.getString(
                    R.string.unknownError, (it.obj as NetworkResponse?)?.statusCode.toString()
                ), Snackbar.LENGTH_SHORT
            )
        }
    )

    companion object : PageCompanion()

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
                if (!b.rv.canScrollVertically(1) && thread?.active != true &&
                    c.mm.vwUser?.hasMore() == true
                ) thread = FetchSome().also { it.start() }
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
            if (c.mm.vwUser?.hdPhoto() == null) return@setOnClickListener
            MaterialMenu(c, v, R.menu.viewer_pic_more, Act().apply {
                this[R.id.vpDownload] = {
                    CoroutineScope(Dispatchers.IO).launch {
                        c.dao.addQueued(
                            Queued(
                                Persistent.now(), "", Persistent.now(), c.mm.vwUser!!.id,
                                c.user, "profile_photo", c.mm.vwUser!!.hdPhoto(),
                                c.mm.vwUser!!.hdPhoto(), 1
                            )
                        )
                        withContext(Dispatchers.Main) { Downloads.initService(c, "") }
                    }
                }
            }).show()
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
                    Saver(tracker!!.selection).start()
                tracker?.clearSelection()
            }
            R.id.vtSelectAll -> if (c.mm.vwUser?.edges() != null)
                tracker?.setItemsSelected(c.mm.vwUser!!.edges()!!.map { it.node.id }, true)
            R.id.vtDeselectAll -> tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    fun showProfile() {
        if (!com.active || c.mm.vwUser == null || !bInitialised) return
        Glide.with(c.c)
            .load(c.mm.vwUser!!.hdPhoto())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .addListener(GlideShimmer(b.proPic, b.proPicIv))
            .into(b.proPicIv)
        b.followersNum.text = c.mm.vwUser!!.edge_followed_by.toString()
        b.followingNum.text = c.mm.vwUser!!.edge_follow.toString()
        handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()

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

    inner class FetchSome : BaseThread() {
        override fun run() {
            if (c.mm.vwUser == null) {
                interrupt()
                return; }
            c.reqQueue.adder = Api<GraphQl>(
                c, Api.Endpoint.POSTS.url.format(
                    c.mm.vwUser!!.id,
                    c.mm.vwUser!!.edge_owner_to_timeline_media!!.page_info.end_cursor
                ), GraphQl::class, handler, autoQueue = false, onError = { interrupt() }
            ) { res ->
                val add = res.data?.user?.edge_owner_to_timeline_media
                if (add == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget()
                    interrupt(); return@Api; }
                c.mm.vwUser?.edge_owner_to_timeline_media?.apply {
                    page_info = add.page_info
                    count = add.count
                    val lastBefore = edges.size
                    val ids = edges.map { it.node.id }
                    edges.addAll(add.edges.filter { it.node.id !in ids })
                    handler?.obtainMessage(HANDLE_FETCHED, lastBefore, add.edges.size)
                        ?.sendToTarget()
                }
            }
        }
    }

    inner class Saver(selection: Selection<String>) : SelectionHandler(selection) {

        override fun handle() {
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
