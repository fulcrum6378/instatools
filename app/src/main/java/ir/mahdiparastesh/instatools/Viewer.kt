package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.MutableLiveData
import androidx.media2.common.SessionPlayer
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
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
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListVwr
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_ABORTED
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_FETCHED
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.view.*
import ir.mahdiparastesh.instatools.view.UiTools.Companion.accFromUrl
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// TAGGED -> MediaWrapperApi
// STORY -> Story
// HIGHLIGHTS -> Highlights

class Viewer : BaseActivity(), Toolbar.OnMenuItemClickListener {
    lateinit var b: ViewerBinding
    private var user: String? = null
    private var thread: FetchSome? = null
    var tracker: SelectionTracker<String>? = null
    private var dbFav: Favourite? = null

    override val menuRes = R.menu.viewer_tlb
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion() {
        private const val EXTRA_USER = "EXTRA_USER"

        fun comeHere(c: BaseActivity, user: String) {
            c.goTo(Viewer::class) { putExtra(EXTRA_USER, user) }
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
                        if (msg.obj == true) {
                            Glide.with(c)
                                .load(m.vwUser!!.photo())
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .addListener(GlideShimmer(b.proPic, b.proPicIv))
                                .into(b.proPicIv)
                            b.followersNum.text = m.vwUser!!.edge_followed_by.toString()
                            b.followingNum.text = m.vwUser!!.edge_follow.toString()
                        }

                        if (msg.arg1 != msg.arg2) adapt(msg.arg1, msg.arg2) else adapt()
                        b.refresher.isRefreshing = false
                        if (m.vwUser!!.hasMore() && thread?.active != true
                            && !b.rv.canScrollVertically(1)
                        ) thread = FetchSome().also { it.start() }

                        val showPv = m.vwUser!!.pv() && m.vwUser!!.followed_by_viewer == false
                        b.privateAcc.vis(showPv)
                        b.rv.vis(!showPv)
                        if (!showPv) return
                        b.privateAcc.setCompoundDrawablesWithIntrinsicBounds(
                            null, drawable(
                                R.drawable.private_account, if (night()) R.color.defCA else null
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
                        b.refresher.isRefreshing = false
                        Snackbar.make(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG).show()
                    }
                    PageSvd.HANDLE_INIT_QUEUER -> Downloads.initService(this@Viewer)
                    Api.HANDLE_ERROR -> {
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
        b.refresher.setOnRefreshListener {
            reset()
            if (thread?.active != true) thread = FetchSome().also { it.start() }
        }
        b.nsv.viewTreeObserver.addOnScrollChangedListener {
            b.tbShadow.vish(b.nsv.scrollY > 0)
            b.rv.isNestedScrollingEnabled = !b.nsv.canScrollVertically(1)
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateJumper()
                if (!b.rv.canScrollVertically(1) && thread?.active != true &&
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
        Delay(1500) { b.rv.layoutParams = b.rv.layoutParams.apply { height = b.nsv.height } }

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
                                Persistent.now(), "", Persistent.now(), m.vwUser!!.id, user,
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

        if (m.vwUser == null) load()
        else handler?.obtainMessage(HANDLE_FETCHED, true)?.sendToTarget()
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

    fun fixTbMenu() {
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

    private fun adapt(begin: Int? = null, count: Int? = null) {
        if (b.rv.adapter != null && begin != null && count != null) {
            b.rv.adapter?.notifyItemRangeInserted(begin, count)
            return; }
        b.rv.adapter = ListVwr(this)
        (b.rv.adapter as ListVwr?)?.let { adp ->
            tracker = SelectionTracker.Builder(
                "viewer", b.rv,
                adp.PostKeyProvider(), ListPost.PostDetailsLookup(b.rv),
                StorageStrategy.createStringStorage()
            ).build().also { it.addObserver(adp.SelectObserver()) }
        }
    }

    private fun flwClick(isItFollowers: Boolean, v: View) {
        if (m.vwUser?.access() != true || m.vwUser?.id == null || Main.guest) return
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
                            Follower.ToBeEnqueued(m.vwUser!!.id, isItFollowers, false)
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

    override fun onPause() {
        super.onPause()
        (b.expanded.slider.adapter as ListCar?)?.players
            ?.forEach { if (it?.playerState == SessionPlayer.PLAYER_STATE_PLAYING) it.pause() }
    }

    override fun onBackPressed() {
        if (::b.isInitialized) {
            val adp = (b.rv.adapter as ListVwr?)
            if (adp != null && adp.expandable.zoomed) {
                adp.expandable.collapse(); return; }
        }
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return
        }
        m.vwUser = null
        super.onBackPressed()
    }

    inner class FetchSome : BaseThread() {
        override fun run() {
            if (m.vwUser == null) Api<Profile>(
                this@Viewer, Api.Type.PROFILE.url.format(user), Profile::class,
                handler, onError = { interrupt() }
            ) { profile ->
                m.vwUser = profile.graphql?.user
                if (m.vwUser == null) {
                    Toast.makeText(c, R.string.pageNotExist, Toast.LENGTH_SHORT).show()
                    interrupt(); return@Api
                }
                done()
            } else Api<Profile.GraphQlResponse>(
                this@Viewer, Api.Type.POSTS.url.format(
                    // I was gonna give the "id" (Thread.getId()) of Java thread to this API... XD
                    m.vwUser!!.id, m.vwUser!!.edge_owner_to_timeline_media!!.edges.size,
                    m.vwUser!!.edge_owner_to_timeline_media!!.page_info.end_cursor
                ), Profile.GraphQlResponse::class, handler, onError = { interrupt() }
            ) { res ->
                val edgeList = res.data.user?.edge_owner_to_timeline_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget()
                    interrupt(); return@Api; }
                done(edgeList)
            }
        }

        private fun done(add: Profile.EdgeList? = null) {
            if (add != null) m.vwUser?.edge_owner_to_timeline_media?.apply {
                page_info = add.page_info
                count = add.count
                val lastBefore = edges.size
                edges.addAll(add.edges)
                handler?.obtainMessage(HANDLE_FETCHED, lastBefore, add.edges.size)?.sendToTarget()
            } else handler?.obtainMessage(HANDLE_FETCHED, true)?.sendToTarget()
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
                    Queued(Persistent.now(), Api.Type.POST_ITEM.url.format(edge.node.shortcode))
                )
            }
            ended()
        }
    }
}
