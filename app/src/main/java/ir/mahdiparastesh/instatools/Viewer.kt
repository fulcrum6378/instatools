package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.selection.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.NetworkResponse
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListPrf
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_ABORTED
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_FETCHED
import ir.mahdiparastesh.instatools.more.BaseSaver
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

class Viewer : BaseActivity(), Toolbar.OnMenuItemClickListener {
    lateinit var b: ViewerBinding
    override val menuRes = R.menu.viewer_tlb
    private lateinit var db: Database
    lateinit var dao: Database.DAO
    private var user: String? = null
    private var id: String? = null
    private var thread: FetchSome? = null
    var tracker: SelectionTracker<String>? = null
    private var selectivity = false

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
                        b.refresher.isRefreshing = false
                        Snackbar.make(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG).show()
                    }
                    Api.HANDLE_ERROR -> {
                        b.refresher.isRefreshing = false
                        Snackbar.make(
                            b.root, c.getString(
                                R.string.unknownError,
                                (msg.obj as NetworkResponse).statusCode.toString()
                            ), Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    Expandable.HANDLE_EXPANDABLE_ERROR ->
                        Snackbar.make(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        b.toolbar.setOnMenuItemClickListener(this)

        b.rv.layoutManager = GridLayoutManager(c, 3)
        b.refresher.setOnRefreshListener {
            b.rv.adapter = null
            m.vwInfo = null
            m.vwEdges = null
            tracker = null
            if (thread?.active != true) thread = FetchSome().also { it.start() }
        }
        b.rv.viewTreeObserver.addOnScrollChangedListener {
            if ((b.rv.computeVerticalScrollExtent() + b.rv.computeVerticalScrollOffset() +
                        (dm.heightPixels * 0.1)) >= b.rv.computeVerticalScrollRange() &&
                thread?.active != true && m.vwInfo?.has_next_page != false
            ) thread = FetchSome().also { it.start() }
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
            }
        })
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

    fun selective(bb: Boolean) {
        b.toolbar.menu.clear()
        b.toolbar.inflateMenu(if (bb) R.menu.viewer_tlb_select else R.menu.viewer_tlb)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.vtInsta -> {
            UiTools.openProfile(this, user!!); true; }

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
        m.vwInfo = null
        m.vwEdges = null
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
                /*Glide.with(c)
                    .load(u.profile_pic_url_hd ?: u.profile_pic_url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .addListener(GlideShimmer(b.proPic, b.proPicIv))
                    .into(b.proPicIv)*/
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
                Downloads.initService(this@Viewer)
                return
            }
            m.vwEdges?.find { it.id == svd }?.let { post ->
                dao.addQueued(Queued(Queuer.now(), Api.Type.POST.url.format(post.shortcode)))
            }
            ended()
        }
    }
}
