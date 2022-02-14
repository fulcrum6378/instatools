package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.selection.*
import androidx.recyclerview.widget.GridLayoutManager
import com.android.volley.NetworkResponse
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListPrf
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_ABORTED
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_FETCHED
import ir.mahdiparastesh.instatools.more.BaseSaver
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.*
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

class Viewer : BaseActivity(), Toolbar.OnMenuItemClickListener {
    lateinit var b: ViewerBinding
    override val menuRes = R.menu.viewer_tlb
    private var user: String? = null
    private var id: String? = null
    private var thread: FetchSome? = null
    var tracker: SelectionTracker<String>? = null
    private var selectivity = false
    private var dbFav: Favourite? = null

    companion object {
        private const val EXTRA_USER = "EXTRA_USER"
        private const val EXTRA_ID = "EXTRA_ID"
        var handler: Handler? = null

        fun comeHere(c: BaseActivity, id: String, user: String) {
            c.startActivity(Intent(c.c, Viewer::class.java).apply {
                putExtra(EXTRA_USER, user)
                putExtra(EXTRA_ID, id)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.extras?.getString(EXTRA_USER)?.let { user = it }
        intent.extras?.getString(EXTRA_ID)?.let { id = it }
        if (user == null || id == null) {
            onBackPressed(); return; }

        b = ViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.vwTitle, changeTitleTo = user)
        db = Database.build(c, m.acc!!.id.toString()).also { dao = it.dao() }

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_FETCHED -> {
                        adapt()
                        b.refresher.isRefreshing = false
                        if (!m.vwEdges.isNullOrEmpty() && m.vwInfo?.has_next_page == true &&
                            (m.vwEdges!!.size / 3) * (dm.widthPixels / 3) < dm.heightPixels
                        ) thread = FetchSome().also { it.start() }
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

        // Toolbar
        b.toolbar.setOnMenuItemClickListener(this)

        // List
        b.rv.layoutManager = GridLayoutManager(c, 3)
        b.rv.isNestedScrollingEnabled = false
        b.refresher.setOnRefreshListener {
            reset()
            if (thread?.active != true) thread = FetchSome().also { it.start() }
        }
        b.nsv.viewTreeObserver.addOnScrollChangedListener {
            b.tbShadow.vish(b.nsv.scrollY > 0)
            if (!b.nsv.canScrollVertically(1) && thread?.active != true &&
                m.vwInfo?.has_next_page != false
            ) thread = FetchSome().also { it.start() }
        }

        // Profile
        b.proPic.layoutParams = b.proPic.layoutParams.apply { height = dm.widthPixels }
        b.proClick.setOnClickListener { v ->
            if (m.vwFav?.photo == null) return@setOnClickListener
            MaterialMenu(this@Viewer, v, R.menu.viewer_pic_more, Act().apply {
                this[R.id.vpDownload] = {
                    dao.addQueued(
                        Queued(
                            Queuer.now(), "", Queuer.now(), id, user, "profile_photo",
                            m.vwFav!!.photo, m.vwFav!!.photo, 1
                        )
                    )
                    Downloads.initService(this@Viewer)
                }
            }).show()
        }

        load()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.extras?.containsKey(EXTRA_USER) == true && intent.extras?.containsKey(EXTRA_ID) == true) {
            intent.extras?.getString(EXTRA_USER)?.let { user = it }
            intent.extras?.getString(EXTRA_ID)?.let { id = it }
            load()
            b.proPicIv.setImageDrawable(null)
            b.toolbar.title = user
        }
    }

    private fun load() {
        reset()
        dbFav = dao.favourite(id!!).getOrNull(0)
        fixTbMenu()
        if (m.vwEdges != null) adapt()
        else if (thread?.active != true) thread = FetchSome().also { it.start() }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter != null) {
            b.rv.adapter?.notifyDataSetChanged()
            return; }
        b.rv.adapter = ListPrf(this)
        if (tracker == null) {
            tracker = SelectionTracker.Builder(
                "viewer", b.rv,
                MyItemKeyProvider(), MyDetailsLookup(),
                StorageStrategy.createStringStorage()
            ).build()
            tracker?.addObserver(SelectObserver())
        }
    }

    private fun reset() {
        b.rv.adapter = null
        m.vwInfo = null
        m.vwEdges = null
        tracker = null
        (b.rv.adapter as ListPrf?)?.let { if (it.expandable.zoomed) it.expandable.collapse() }
    }

    fun selective(bb: Boolean) {
        b.toolbar.menu.clear()
        b.toolbar.inflateMenu(if (bb) R.menu.viewer_tlb_select else R.menu.viewer_tlb)
        fixTbMenu()
    }

    private fun fixTbMenu() {
        b.toolbar.menu.findItem(R.id.vtFav)
            ?.setIcon(if (dbFav != null) R.drawable.favourite else R.drawable.non_favourite)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        fixTbMenu()
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.vtInsta -> {
            UiTools.openProfile(this, user!!); true; }
        R.id.vtFav -> {
            if (m.vwFav != null) {
                if (dbFav == null) {
                    dbFav = m.vwFav!!
                    dao.addFavourite(dbFav!!)
                } else {
                    dao.deleteFavourite(dbFav!!)
                    dbFav = null
                }
                fixTbMenu()
            }; true; }

        R.id.vtDownload -> {
            if (tracker != null && m.vwEdges != null)
                Saver(tracker!!.selection).start()
            tracker?.clearSelection()
            true
        }
        R.id.vtSelectAll -> {
            if (m.vwEdges != null) tracker?.setItemsSelected(m.vwEdges!!.map { it.id }, true)
            true
        }
        R.id.vtDeselectAll -> {
            tracker?.clearSelection()
            true
        }
        else -> false
    }

    override fun onDestroy() {
        handler = null
        m.vwEdges = null
        m.vwInfo = null
        m.vwFav = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        (b.rv.adapter as ListPrf?)?.let {
            if (it.expandable.zoomed) {
                it.expandable.collapse(); return; }
        }
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return
        }
        super.onBackPressed()
    }

    inner class MyItemKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String = m.vwEdges!![i].id
        override fun getPosition(key: String): Int {
            for (i in m.vwEdges!!.indices) if (m.vwEdges!![i].id == key) return i
            return -1
        }
    }

    inner class MyDetailsLookup : ItemDetailsLookup<String?>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<String?>? {
            b.rv.findChildViewUnder(e.x, e.y)?.let {
                val h = b.rv.getChildViewHolder(it)
                if (h is ListPrf.ViewHolder) return@getItemDetails h.getItemDetails()
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

    inner class FetchSome : BasePage.BaseThread() {
        override fun run() {
            if (m.vwEdges == null) Api<Profile>(
                this@Viewer, Api.Type.PROFILE.url.format(user), Profile::class, handler
            ) { profile ->
                val u = profile.graphql?.user
                if (u == null) {
                    Toast.makeText(c, "This page doesn\'t exist!", Toast.LENGTH_SHORT).show()
                    return@Api
                }

                m.vwFav = Favourite(
                    this@Viewer.id!!, u.username, u.full_name,
                    u.profile_pic_url_hd ?: u.profile_pic_url,
                    u.is_private == true
                )
                Glide.with(c)
                    .load(m.vwFav!!.photo)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .addListener(GlideShimmer(b.proPic, b.proPicIv))
                    .into(b.proPicIv)
                val edgeList = u.edge_owner_to_timeline_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget(); return@Api; }
                done(edgeList)
            } else Api<Profile.GraphQlResponse>(
                this@Viewer, Api.Type.POSTS.url.format(id, m.vwEdges!!.size, m.vwInfo!!.end_cursor),
                Profile.GraphQlResponse::class, handler
            ) { res ->
                val edgeList = res.data.user?.edge_owner_to_timeline_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget(); return@Api; }
                done(edgeList)
            }
        }

        private fun done(media: Profile.EdgeList) {
            m.vwInfo = media.page_info
            if (m.vwEdges == null) m.vwEdges = arrayListOf()
            media.edges.map { it.node }.forEach { post ->
                m.vwEdges?.removeAll { it.id == post.id }
                m.vwEdges?.add(post)
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
                return
            }
            m.vwEdges?.find { it.id == svd }?.let { post ->
                dao.addQueued(Queued(Queuer.now(), Api.Type.POST.url.format(post.shortcode)))
            }
            ended()
        }
    }
}
