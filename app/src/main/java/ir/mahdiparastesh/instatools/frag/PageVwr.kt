package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
import ir.mahdiparastesh.instatools.databinding.FollowerOptionsBinding
import ir.mahdiparastesh.instatools.databinding.PageVwrBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListVwr
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.GlideShimmer
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageVwr : BasePageViewer() {
    lateinit var b: PageVwrBinding
    private var thread: FetchSome? = null

    override val com: PageCompanion = Companion
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout get() = b.root
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_FETCHED to { msg ->
            if (b.rv.adapter != null && msg.arg2 > 0) {
                super.onLoaded(c.m.vwUser?.edges().isNullOrEmpty(), false)
                b.rv.adapter?.notifyItemRangeInserted(msg.arg1, msg.arg2)
            } else onLoaded(c.m.vwUser?.edges().isNullOrEmpty())

            if (c.m.vwUser?.hasMore() == true && thread?.active != true
                && !b.rv.canScrollVertically(1)
            ) thread = FetchSome().also { it.start() }

            val showPv = c.m.vwUser?.pv() == true && c.m.vwUser?.followed_by_viewer == false
            b.privateAcc.vis(showPv)
            b.rv.vis(!showPv)
            if (showPv) {
                b.privateAcc.setCompoundDrawablesWithIntrinsicBounds(
                    null, c.drawable(
                        R.drawable.private_account, if (c.night()) R.color.defCA else null
                    )!!, null, null
                )
                b.privateAcc.typeface = c.fontRegular
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
            Snackbar.make(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG).show()
        },
        Api.HANDLE_ERROR to {
            Snackbar.make(
                b.root, c.getString(
                    R.string.unknownError,
                    (it.obj as NetworkResponse?)?.statusCode.toString()
                ), Snackbar.LENGTH_SHORT
            ).show()
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
                    c.m.vwUser?.hasMore() == true
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
            if (c.m.vwUser?.photo() == null) return@setOnClickListener
            MaterialMenu(c, v, R.menu.viewer_pic_more, Act().apply {
                this[R.id.vpDownload] = {
                    CoroutineScope(Dispatchers.IO).launch {
                        c.dao.addQueued(
                            Queued(
                                Persistent.now(), "", Persistent.now(), c.m.vwUser!!.id,
                                c.user, "profile_photo", c.m.vwUser!!.photo(),
                                c.m.vwUser!!.photo(), 1
                            )
                        )
                        withContext(Dispatchers.Main) { Downloads.initService(c, "") }
                    }
                }
            }).show()
        }
        arrayOf(b.followersNum, b.followingNum).forEach { it.typeface = c.fontBold }
        arrayOf(b.followersText, b.followingText).forEach { it.typeface = c.fontLight }
        b.followers.setOnClickListener { flwClick(true, it) }
        b.following.setOnClickListener { flwClick(false, it) }
        showProfile()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload -> {
                if (tracker != null && c.m.vwUser?.edges() != null)
                    Saver(tracker!!.selection).start()
                tracker?.clearSelection()
            }
            R.id.vtSelectAll -> if (c.m.vwUser?.edges() != null)
                tracker?.setItemsSelected(c.m.vwUser!!.edges()!!.map { it.node.id }, true)
            R.id.vtDeselectAll -> tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    fun showProfile() {
        if (com.active.value != true || c.m.vwUser == null || !bInitialised) return
        Glide.with(c.c)
            .load(c.m.vwUser!!.photo())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .addListener(GlideShimmer(b.proPic, b.proPicIv))
            .into(b.proPicIv)
        b.followersNum.text = c.m.vwUser!!.edge_followed_by.toString()
        b.followingNum.text = c.m.vwUser!!.edge_follow.toString()
        handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()

        if (c.m.vwUser != null) c.dbFav?.apply {
            var changed = false
            if (user != c.m.vwUser!!.username) {
                user = c.m.vwUser!!.username
                changed = true
            }
            if (name != c.m.vwUser!!.full_name) {
                name = c.m.vwUser!!.full_name
                changed = true
            }
            if (photo != c.m.vwUser!!.profile_pic_url_hd) {
                photo = c.m.vwUser!!.profile_pic_url_hd
                changed = true
            }
            if (changed) Thread { c.dao.updateFavourite(this) }.start()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
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

    private fun flwClick(isItFollowers: Boolean, v: View) {
        if (c.m.vwUser?.access() != true || c.m.vwUser?.id == null || Main.guest ||
            c.m.vwUser?.id == c.m.acc?.id.toString()
        ) return
        MaterialMenu(c, v, R.menu.vwr_flw_more, Act().apply {
            this[R.id.vfFollowAll] = {
                val bo = FollowerOptionsBinding.inflate(layoutInflater)
                bo.alsoRequestPv.typeface = c.fontRegular
                bo.limitTv.typeface = c.fontRegular
                bo.limit.typeface = c.fontBold
                var flwLimit = ((if (isItFollowers) c.m.vwUser?.edge_followed_by?.count
                else c.m.vwUser?.edge_follow?.count) ?: 0.0).toInt()
                if (flwLimit > MassFollower.FOLLOW_LIMIT) flwLimit = MassFollower.FOLLOW_LIMIT
                bo.limit.setText(flwLimit.toString())
                AlertDialog.Builder(c).apply {
                    setTitle(R.string.followAll)
                    setMessage(c.getString(R.string.followAllSure))
                    setView(bo.root)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        MassFollower.initService(
                            c, Follower.ToBeEnqueued(
                                c.m.vwUser!!.id, isItFollowers,
                                bo.alsoRequestPv.isChecked, bo.limit.text.toString().toLong()
                            )
                        ) { c.goTo(MassFollower::class) }
                    }
                }.show().stylise(c)
            }
        }).show()
    }

    override fun avoidRefresh(): Boolean =
        b.nsv.canScrollVertically(-1) || tracker?.hasSelection() == true

    override fun updateShadow() {
        if (bInitialised) c.b.tbShadow.vish(b.nsv.scrollY > 0)
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.m.vwUser?.edges()?.getOrNull(i)?.node?.id
        override fun getPosition(key: String): Int {
            c.m.vwUser?.edges()?.forEachIndexed { i, edge ->
                if (edge.node.id == key) return@getPosition i
            }
            return -1
        }
    }

    inner class FetchSome : BaseThread() {
        override fun run() {
            if (c.m.vwUser == null) {
                interrupt()
                return; }
            Api<Profile.GraphQlResponse>(
                c, Api.Type.POSTS.url.format(
                    // I was gonna give the "id" (Thread.getId()) of Java thread to this API... XD
                    c.m.vwUser!!.id, c.m.vwUser!!.edge_owner_to_timeline_media!!.edges.size,
                    c.m.vwUser!!.edge_owner_to_timeline_media!!.page_info.end_cursor
                ), Profile.GraphQlResponse::class, handler, onError = { interrupt() }
            ) { res ->
                val add = res.data.user?.edge_owner_to_timeline_media
                if (add == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget()
                    interrupt(); return@Api; }
                c.m.vwUser?.edge_owner_to_timeline_media?.apply {
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

    inner class Saver(selection: Selection<String>) : BaseSaver(selection) {
        override fun handle() {
            val edg = list.getOrNull(0)
            if (edg == null) {
                Viewer.handler?.obtainMessage(PageSvd.HANDLE_INIT_QUEUER)?.sendToTarget()
                interrupt()
                return
            }
            c.m.vwUser?.edges()?.find { it.node.id == edg }?.let { edge ->
                c.dao.addQueued(
                    Queued(Persistent.now(), Api.Type.POST_ITEM.url.format(edge.node.shortcode))
                )
            }
            ended()
        }
    }
}
