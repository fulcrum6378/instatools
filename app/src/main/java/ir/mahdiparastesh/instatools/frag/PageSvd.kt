package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Message
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable.INFINITE
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.databinding.PageSvdBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.GraphQl
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.SafeGridManager
import ir.mahdiparastesh.instatools.view.Selective
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.shake
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

@SuppressLint("NotifyDataSetChanged")
class PageSvd : BasePageMain(), Selective {
    lateinit var b: PageSvdBinding
    var thread: FetchSome? = null
    var saver: Saver? = null
    private var selectionBadge: BadgeDrawable? = null
    private var selectionGuide: LottieAnimationView? = null
    private var reallyHasMore = true

    override val com: PageCompanion = Companion
    override val theme: BaseActivity.Theme = BaseActivity.Theme.SECONDARY
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout get() = b.root
    override val emptyIcon: Int = R.drawable.done_svd
    override fun expanded(): ExpandableBinding = b.expanded
    override val selectiveMenuRes: Int = R.menu.main_tlb_svd_select

    @Suppress("UNCHECKED_CAST")
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_FETCHED to { msg ->
            if (b.rv.adapter != null && msg.arg2 > 0) {
                super@PageSvd.onLoaded(c.m.saved?.edges.isNullOrEmpty(), false)
                b.rv.adapter?.notifyItemRangeInserted(msg.arg1, msg.arg2)
                c.bnvBadge(1, c.m.saved?.count?.toInt() ?: 0)
            } else onLoaded(c.m.saved?.edges.isNullOrEmpty())

            if (c.m.saved?.page_info?.has_next_page == true && !b.rv.canScrollVertically(1)
                && thread?.active != true
            ) thread = FetchSome().also { it.start() }
        },
        HANDLE_ABORTED to { onFailed(c.getString(R.string.loadFailed)) },
        Api.HANDLE_ERROR to {
            onFailed(
                c.getString(
                    R.string.unknownError, (it.obj as NetworkResponse?)?.statusCode.toString()
                )
            )
        },
        Expandable.HANDLE_EXPANDABLE_ERROR to {
            try {
                Snackbar.make(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG).show()
            } catch (ignored: IllegalArgumentException) {
            }
        },
        HANDLE_UNSAVE_DONE to { msg ->
            c.m.saved?.apply { if (count > 0.0) count -= 1.0 }
            c.bnvBadge(1, c.m.saved?.count?.toInt() ?: 0)
            c.m.saved?.edges?.find { it.node.id == msg.obj as String }?.let { post ->
                val x = c.m.saved!!.edges.indexOf(post)
                c.m.saved!!.edges.removeAt(x)
                b.rv.adapter?.notifyItemRemoved(x)
                b.rv.adapter?.notifyItemRangeChanged(x, c.m.saved!!.edges.size)
                if (c.m.saved?.edges.isNullOrEmpty()) onLoaded(true)
            }
        },
        HANDLE_SHOW_AD to { c.loadInterstitial(R.string.interUnsaving, true) },
        HANDLE_INIT_QUEUER to {
            Downloads.initService(c, "")
            if (!UiTools.hasReviewedApp(c)) UiTools.reviewApp(c)
        },
        HANDLE_REALLY_NO_MORE to {
            val number = c.m.saved?.hiddenItems()
            if (number != null && number > 0) try {
                Snackbar.make(
                    b.root, c.getString(R.string.reallyHasNoMore, number), 10000
                ).show()
            } catch (ignored: IllegalArgumentException) {
            }
        },
        HANDLE_UPDATE_PROFILE to { msg ->
            val list = msg.obj as List<String>
            c.m.acc?.apply {
                user = list[0]
                name = list[1]
                pict = list[2]
                saveMe(c.c)
            }
        }
    )
    override var tracker: SelectionTracker<String>? = null
    override var selectivity = false

    companion object : PageCompanion() {
        const val HANDLE_UNSAVE_DONE = 10
        const val HANDLE_INIT_QUEUER = 11
        const val HANDLE_SHOW_AD = 12
        const val HANDLE_REALLY_NO_MORE = 13
        const val HANDLE_UPDATE_PROFILE = 14
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageSvdBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Main.guest) return

        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback tracker?.hasSelection() == true
                    || selectionGuide != null
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!b.rv.canScrollVertically(1) && reallyHasMore &&
                    thread?.active != true && c.m.saved?.page_info?.has_next_page != false
                ) thread = FetchSome().also { it.start() }
            }
        })
        b.rv.layoutManager = object : SafeGridManager(c, 3) {
            override fun canScrollVertically(): Boolean =
                super.canScrollVertically() && selectionGuide == null
        }

        if (c.m.saved != null) onLoaded(c.m.saved?.edges.isNullOrEmpty())
        else if (thread?.active != true) thread = FetchSome().also { it.start() }
    }

    override fun onStart() {
        super.onStart()
        if (bInitialised && b.rv.adapter != null && ftDetached) buildSelection()
    }

    override fun onRefresh() {
        if (thread?.active == true) return
        c.m.saved = null
        b.rv.adapter?.notifyDataSetChanged()
        b.empty.vis(false)
        tracker?.clearSelection()
        reallyHasMore = true
        thread = FetchSome().also { it.start() }
    }

    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
        if (!asGuest) c.bnvBadge(1, c.m.saved?.count?.toInt() ?: 0)

        if (b.rv.adapter == null) b.rv.adapter = ListSvd(c, this)
        else b.rv.adapter?.notifyDataSetChanged() // created only once

        // Selection Guide
        if (!asGuest && !isEmpty && !c.gsp.getBoolean(Settings.spLearntSelection, false)
            && selectionGuide == null
        ) selectionGuide = LottieAnimationView(c).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topToTop = ConstraintLayout.LayoutParams.PARENT_ID }
            repeatCount = INFINITE
            setAnimation(R.raw.guide_selection)
            playAnimation()
            translationX = c.dm.widthPixels * -0.12f
            translationY = c.dm.widthPixels * -0.01f
            b.root.addView(this, 1)
        }

        if (tracker == null) buildSelection()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mtUnsaveDownload -> {
                if (tracker != null && c.m.saved != null && saver?.active != true) saver = Saver(
                    tracker!!.selection, unsave = true, download = true
                ).also { it.start() }
                tracker?.clearSelection()
            }
            R.id.mtDownload -> {
                if (tracker != null && c.m.saved != null && saver?.active != true) saver = Saver(
                    tracker!!.selection, unsave = false, download = true
                ).also { it.start() }
                tracker?.clearSelection()
            }
            R.id.mtUnsave -> {
                if (tracker != null && c.m.saved != null && saver?.active != true) saver = Saver(
                    tracker!!.selection, unsave = true, download = false
                ).also { it.start() }
                tracker?.clearSelection()
            }
            R.id.mtSelectAll -> if (c.m.saved != null)
                tracker?.setItemsSelected(c.m.saved!!.edges.map { it.node.id }, true)
            R.id.mtDeselectAll -> tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    override fun buildSelection() {
        // created only once, except after FragmentTransaction::attach()
        tracker = SelectionTracker.Builder(
            "saved", b.rv,
            PostKeyProvider(), ListPost.PostDetailsLookup(b.rv),
            StorageStrategy.createStringStorage()
        ).build().also { it.addObserver(SelectObserver()) }
    }

    override fun updateShadow() {
        if (bInitialised) c.b.tbShadow.vish(
            rv().computeVerticalScrollOffset() > 0 && (b.rv.adapter as ListSvd?)?.expandable?.zoomed != true
        )
    }

    override fun goBack(): Boolean {
        (b.rv.adapter as ListSvd?)?.let {
            if (it.expandable.zoomed) {
                jumper().vis(true)
                it.expandable.collapse(); return@goBack true; }
        }
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return true
        }
        return false
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.m.saved?.edges?.getOrNull(i)?.node?.id
        override fun getPosition(key: String): Int {
            c.m.saved?.edges?.forEachIndexed { i, edge ->
                if (edge.node.id == key) return@getPosition i
            }
            return -1
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    inner class SelectObserver : SelectionTracker.SelectionObserver<String>() {
        override fun onItemStateChanged(key: String, selected: Boolean) {
            if (c.tbTitle == null) return
            BadgeUtils.detachBadgeDrawable(selectionBadge, c.tbTitle!!)
            if (c.tbTitle?.parent == null) return
            // to avoid NullPointerException in BadgeDrawable.updateAnchorParentToNotClip
            BadgeUtils.attachBadgeDrawable(
                BadgeDrawable.create(ContextThemeWrapper(c, UiTools.materialTheme)).apply {
                    number = tracker?.selection?.size() ?: 0
                    backgroundColor = c.ca[1]
                    badgeTextColor = if (!c.night()) c.bg[1] else c.color(R.color.defBG)
                    selectionBadge = this
                }, c.tbTitle!!
            )
        }

        override fun onSelectionChanged() {
            super.onSelectionChanged()
            val status = tracker?.hasSelection() == true
            if (selectivity == status) return
            selectivity = status
            c.selective(status)
            c.shake()
            if (!status) BadgeUtils.detachBadgeDrawable(selectionBadge, c.tbTitle!!)
            else if (selectionGuide != null) {
                b.root.removeView(selectionGuide)
                c.gsp.edit().putBoolean(Settings.spLearntSelection, true).apply()
                b.rv.suppressLayout(false)
            }
        }
    }

    inner class FetchSome : BaseThread() {
        override fun run() {
            if (c.m.saved?.page_info?.has_next_page == false || c.m.acc == null) return
            super.run()
            if (c.m.saved == null) Api<GraphQl>(
                c, Api.Endpoint.PROFILE.url.format(c.m.acc!!.user), GraphQl::class,
                handler, onError = { interrupt() }
            ) { graphql ->
                if (!active) return@Api
                val edgeList = graphql.data.user?.edge_saved_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget()
                    interrupt(); return@Api; }
                c.m.saved = edgeList
                graphql.data.user.also { u ->
                    if (c.m.acc!!.user != u.username || c.m.acc!!.name != u.full_name ||
                        c.m.acc!!.pict != (u.profile_pic_url_hd ?: u.profile_pic_url)
                    ) handler?.obtainMessage(
                        HANDLE_UPDATE_PROFILE,
                        listOf(u.username, u.full_name, u.profile_pic_url_hd ?: u.profile_pic_url)
                    )?.sendToTarget()
                }
                done(null)
            } else Api<GraphQl>(
                c, Api.Endpoint.SAVED.url.format(
                    c.m.acc!!.id,
                    c.m.saved!!.edges.size,
                    c.m.saved?.page_info?.end_cursor ?: ""
                ), GraphQl::class, handler, onError = { interrupt() }
            ) { res ->
                if (!active) return@Api
                val edgeList = res.data.user?.edge_saved_media
                if (edgeList == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget()
                    interrupt(); return@Api; }
                if (edgeList.edges.isEmpty()) {
                    reallyHasMore = false
                    handler?.obtainMessage(HANDLE_REALLY_NO_MORE)?.sendToTarget()
                    interrupt(); return@Api; }
                done(edgeList)
            }
        }

        private fun done(add: GraphQl.EdgeList? = null) {
            if (!active) return
            if (add != null) c.m.saved?.apply {
                page_info = add.page_info
                count = add.count
                edges.removeAll { it.node.id in add.edges.map { addable -> addable.node.id } }
                val lastBefore = edges.size
                edges.addAll(add.edges)
                handler?.obtainMessage(HANDLE_FETCHED, lastBefore, add.edges.size)?.sendToTarget()
            } else handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            interrupt()
        }
    }

    inner class Saver(
        selection: Selection<String>, private val unsave: Boolean, private val download: Boolean
    ) : BaseSaver(selection) {

        override fun run() {
            if (unsave && list.size > 4)
                handler?.obtainMessage(HANDLE_SHOW_AD)?.sendToTarget()
            super.run()
        }

        override fun handle() {
            val svd = list.getOrNull(0)
            if (svd == null) {
                if (download) handler?.obtainMessage(HANDLE_INIT_QUEUER)?.sendToTarget()
                interrupt()
                return; }
            val post = c.m.saved?.edges?.find { it.node.id == svd }?.node
            if (post == null) {
                ended(); return; }

            if (download) try {
                c.dao.addQueued(Queued(Persistent.now(), UiTools.POST_LINK.format(post.shortcode)))
            } catch (e: IllegalStateException) { // DB is closed
            }
            if (unsave) Api<Rest>(
                c, Api.Endpoint.UNSAVE.url.format(post.id), Rest::class, null,
                method = Request.Method.POST, onError = { ended() }
            ) { rest ->
                if (rest.status == "ok")
                    handler?.obtainMessage(HANDLE_UNSAVE_DONE, svd)?.sendToTarget()
                ended()
            }
            if (!unsave) ended()
        }
    }
}
