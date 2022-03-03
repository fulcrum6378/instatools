package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.selection.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.NetworkResponse
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListVwr
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_ABORTED
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_FETCHED
import ir.mahdiparastesh.instatools.more.BaseSaver
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.view.*
import ir.mahdiparastesh.instatools.view.UiTools.Companion.accFromUrl
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Viewer : BaseActivity(), Toolbar.OnMenuItemClickListener {
    lateinit var b: ViewerBinding
    private var user: String? = null
    private var id: String? = null
    private var thread: FetchSome? = null
    var tracker: SelectionTracker<String>? = null
    private var selectivity = false
    private var dbFav: Favourite? = null

    override val menuRes = R.menu.viewer_tlb
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion() {
        private const val EXTRA_USER = "EXTRA_USER"
        private const val EXTRA_ID = "EXTRA_ID"

        fun comeHere(c: BaseActivity, id: String, user: String) {
            c.goTo(Viewer::class) {
                putExtra(EXTRA_USER, user)
                putExtra(EXTRA_ID, id)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resolvedIntent == false) return
        b = ViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.vwTitle, changeTitleTo = user)

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_FETCHED -> {
                        adapt()
                        b.refresher.isRefreshing = false
                        if (m.vwUser!!.hasMore() &&
                            (m.vwUser!!.edges()!!.size / 3) * (dm.widthPixels / 3) < dm.heightPixels
                        ) thread = FetchSome().also { it.start() }

                        val showPv = m.vwUser!!.pv() && m.vwUser!!.followed_by_viewer == false
                        b.privateAcc.vis(showPv)
                        b.rv.vis(!showPv)
                        if (!showPv) return
                        b.privateAcc.setCompoundDrawablesWithIntrinsicBounds(
                            null, drawable(
                                R.drawable.private_account,
                                if (night()) R.color.defCA else null
                            )!!, null, null
                        )
                        b.privateAcc.typeface = fontRegular
                        b.privateAcc.layoutParams =
                            (b.privateAcc.layoutParams as ViewGroup.MarginLayoutParams).apply {
                                val vPad = ((dm.heightPixels.toFloat()
                                        - dm.widthPixels.toFloat()) * 0.19f).toInt()
                                topMargin = vPad
                                bottomMargin = vPad
                            }
                    }
                    HANDLE_ABORTED -> {
                        thread?.interrupt()
                        b.refresher.isRefreshing = false
                        Snackbar.make(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG).show()
                    }
                    PageSvd.HANDLE_INIT_QUEUER -> Downloads.initService(this@Viewer)
                    Api.HANDLE_ERROR -> {
                        thread?.interrupt()
                        b.refresher.isRefreshing = false
                        Snackbar.make(
                            b.root, c.getString(
                                R.string.unknownError,
                                (msg.obj as NetworkResponse?)?.statusCode.toString()
                            ), Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    Expandable.HANDLE_EXPANDABLE_ERROR ->
                        Snackbar.make(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        // List
        b.rv.layoutManager = GridLayoutManager(c, 3)
        b.rv.isNestedScrollingEnabled = false
        b.refresher.setOnRefreshListener {
            reset()
            if (thread?.active != true) thread = FetchSome().also { it.start() }
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                b.tbShadow.vish(b.nsv.scrollY > 0)
                updateJumper()
                if (!b.nsv.canScrollVertically(1) && thread?.active != true &&
                    m.vwUser?.hasMore() != false
                ) thread = FetchSome().also { it.start() }
            }
        })
        b.jumper.setOnClickListener { b.rv.smoothScrollToPosition(0) }
        b.jumper.translationY = UiTools.jumperTrans(this)
        shouldShowJumper.observe(this) {
            anJumper?.cancel()
            anJumper = UiTools.anJumper(this, b.jumper, it)
        }

        // Profile
        b.proPic.layoutParams = b.proPic.layoutParams.apply {
            height = dm.widthPixels
        }
        b.proClick.setOnClickListener { v ->
            if (m.vwUser?.photo() == null) return@setOnClickListener
            MaterialMenu(this@Viewer, v, R.menu.viewer_pic_more, Act().apply {
                this[R.id.vpDownload] = {
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.addQueued(
                            Queued(
                                Persistent.now(), "", Persistent.now(), id, user,
                                "profile_photo", m.vwUser!!.photo(), m.vwUser!!.photo(), 1
                            )
                        )
                        withContext(Dispatchers.Main) { Downloads.initService(this@Viewer) }
                    }
                }
            }).show()
        }
        arrayOf(b.followersNum, b.followingNum).forEach { it.typeface = fontBold }
        arrayOf(b.followersText, b.followingText).forEach { it.typeface = fontLight }
        b.followers.setOnClickListener { flwClick(true, it) }
        b.following.setOnClickListener { flwClick(false, it) }

        load()
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.extras?.getString(EXTRA_USER)?.let {
            if (!onCreation && user == it) return false
            user = it
        }
        intent.data?.let {
            var newUser: String? = null
            for (host in UiTools.ACC_FROM_URL)
                it.toString().accFromUrl(host)
                    ?.let { u -> if (newUser == null) newUser = u }
            if (newUser == null) return@let
            if (!onCreation && newUser == it.toString()) return false
            user = newUser
        }
        intent.extras?.getString(EXTRA_ID)?.let { id = it }
        if (!onCreation) {
            load()
            b.proPicIv.setImageDrawable(null)
            b.toolbar.title = user
            b.privateAcc.vis(false)
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        fixTbMenu()
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtInsta -> UiTools.openProfile(this, user!!)
            R.id.vtFav -> if (m.vwUser != null) CoroutineScope(Dispatchers.IO).launch {
                if (dbFav == null) {
                    dbFav = m.vwUser!!.favourite()
                    dao.addFavourite(dbFav!!)
                } else {
                    dao.deleteFavourite(dbFav!!)
                    dbFav = null
                }
                m.fav = null
                withContext(Dispatchers.Main) { fixTbMenu() }
            }
            R.id.vtDownload -> {
                if (tracker != null && m.vwUser?.edges() != null)
                    Saver(tracker!!.selection).start()
                tracker?.clearSelection()
            }
            R.id.vtSelectAll -> if (m.vwUser?.edges() != null)
                tracker?.setItemsSelected(m.vwUser!!.edges()!!.map { it.node.id }, true)
            R.id.vtDeselectAll -> tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    private fun fixTbMenu() {
        b.toolbar.menu.findItem(R.id.vtFav)
            ?.setIcon(if (dbFav != null) R.drawable.favourite else R.drawable.non_favourite)
    }

    private fun load() {
        reset()
        CoroutineScope(Dispatchers.IO).launch {
            dbFav = dao.favouriteByUser(user!!).getOrNull(0)
            withContext(Dispatchers.Main) { fixTbMenu() }
        }
        if (m.vwUser != null) adapt()
        else if (thread?.active != true) thread = FetchSome().also { it.start() }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter != null) {
            b.rv.adapter?.notifyDataSetChanged()
            return; }
        b.rv.adapter = ListVwr(this)
        if (tracker == null) {
            tracker = SelectionTracker.Builder(
                "viewer", b.rv,
                MyItemKeyProvider(), MyDetailsLookup(),
                StorageStrategy.createStringStorage()
            ).build()
            tracker?.addObserver(SelectObserver())
        }
    }

    private fun flwClick(isItFollowers: Boolean, v: View) {
        if (m.vwUser?.access() != true || id == null || Main.guest) return
        MaterialMenu(this, v, R.menu.vwr_flw_more, Act().apply {
            this[R.id.vfFollowAll] = {
                AlertDialog.Builder(this@Viewer).apply {
                    setTitle(R.string.followAll)
                    setMessage(
                        this@Viewer.getString(
                            R.string.followAllSure,
                            ((if (isItFollowers) m.vwUser?.edge_followed_by?.count else m.vwUser?.edge_follow?.count)
                                ?: 0.0).toInt()
                        )
                    )
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        MassFollower.initService(
                            this@Viewer,
                            Follower.ToBeEnqueued(id!!, isItFollowers, false)
                        )
                        goTo(MassFollower::class)
                    }
                }.show().stylise(this@Viewer)
            }
        }).show()
    }

    private fun reset() {
        b.rv.adapter = null
        m.vwUser = null
        tracker = null
        (b.rv.adapter as ListVwr?)?.let { if (it.expandable.zoomed) it.expandable.collapse() }
    }

    private var shouldShowJumper = MutableLiveData(false)
    private var anJumper: ObjectAnimator? = null
    private fun updateJumper() {
        (b.rv.computeVerticalScrollOffset() > dm.heightPixels)
            .apply { if (this != shouldShowJumper.value) shouldShowJumper.value = this }
    }

    fun selective(bb: Boolean) {
        b.toolbar.menu.clear()
        b.toolbar.inflateMenu(if (bb) R.menu.viewer_tlb_select else R.menu.viewer_tlb)
        fixTbMenu()
    }

    override fun onBackPressed() {
        if (::b.isInitialized) (b.rv.adapter as ListVwr?)?.let {
            if (it.expandable.zoomed) {
                it.expandable.collapse(); return; }
        }
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        m.vwUser = null
        super.onDestroy()
    }

    inner class MyItemKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String = m.vwUser!!.edges()!![i].node.id
        override fun getPosition(key: String): Int {
            for (i in m.vwUser!!.edges()!!.indices)
                if (m.vwUser!!.edges()!![i].node.id == key) return i
            return -1
        }
    }

    inner class MyDetailsLookup : ItemDetailsLookup<String?>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<String?>? {
            b.rv.findChildViewUnder(e.x, e.y)?.let {
                val h = b.rv.getChildViewHolder(it)
                if (h is ListPost<*>.ViewHolder) return@getItemDetails h.getItemDetails()
            }
            return null
        }
    }

    inner class SelectObserver : SelectionTracker.SelectionObserver<String>() {
        override fun onSelectionChanged() {
            super.onSelectionChanged()
            val status = tracker?.hasSelection() == true
            if (selectivity == status) return
            selectivity = status
            selective(status)
            UiTools.shake(c)
        }
    }

    inner class FetchSome : BaseThread() {
        override fun run() {
            if (m.vwUser == null) Api<Profile>(
                this@Viewer, Api.Type.PROFILE.url.format(user), Profile::class, handler
            ) { profile ->
                m.vwUser = profile.graphql?.user
                if (m.vwUser == null) {
                    Toast.makeText(c, R.string.pageNotExist, Toast.LENGTH_SHORT).show()
                    return@Api
                }
                this@Viewer.id = m.vwUser!!.id
                Glide.with(c)
                    .load(m.vwUser!!.photo())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .addListener(GlideShimmer(b.proPic, b.proPicIv))
                    .into(b.proPicIv)
                b.followersNum.text = m.vwUser!!.edge_followed_by.toString()
                b.followingNum.text = m.vwUser!!.edge_follow.toString()
                done()
            } else Api<Profile.GraphQlResponse>(
                this@Viewer, Api.Type.POSTS.url.format(
                    id, m.vwUser!!.edge_owner_to_timeline_media!!.edges.size,
                    m.vwUser!!.edge_owner_to_timeline_media!!.page_info.end_cursor
                ), Profile.GraphQlResponse::class, handler
            ) { res ->
                val edgeList = res.data.user?.edge_owner_to_timeline_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget(); return@Api; }
                done(edgeList)
            }
        }

        private fun done(add: Profile.EdgeList? = null) {
            if (add != null) {
                m.vwUser?.edge_owner_to_timeline_media?.page_info = add.page_info
                m.vwUser?.edge_owner_to_timeline_media?.count = add.count
                m.vwUser?.edge_owner_to_timeline_media?.edges?.addAll(add.edges)
            }
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            interrupt()
        }
    }

    inner class Saver(selection: Selection<String>) : BaseSaver(selection) {
        override fun handle() {
            val svd = list.getOrNull(0)
            if (svd == null) {
                handler?.obtainMessage(PageSvd.HANDLE_INIT_QUEUER)?.sendToTarget()
                interrupt()
                return
            }
            m.vwUser?.edges()?.find { it.node.id == svd }?.let { edge ->
                dao.addQueued(
                    Queued(Persistent.now(), Api.Type.POST.url.format(edge.node.shortcode))
                )
            }
            ended()
        }
    }
}
