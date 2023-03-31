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
import androidx.core.content.edit
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable.INFINITE
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.toolbox.Volley
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.databinding.PageSvdBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.SafeGridManager
import ir.mahdiparastesh.instatools.view.Selective
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.shake
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish

@SuppressLint("NotifyDataSetChanged")
class PageSvd : BasePageMain(), Selective {
    lateinit var b: PageSvdBinding
    var thread: FetchSome? = null
    var saver: Saver? = null
    private var selectionGuide: LottieAnimationView? = null
    val reqQueue by lazy { Volley.newRequestQueue(c) }

    override val com: PageCompanion = Companion
    override val theme: BaseActivity.Theme = BaseActivity.Theme.SECONDARY
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val emptyIcon: Int = R.drawable.done_svd
    override fun expanded(): ExpandableBinding = b.expanded
    override val selectiveMenuRes: Int = R.menu.main_tlb_svd_select

    @Suppress("UNCHECKED_CAST")
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_FETCHED to { msg ->
            if (b.rv.adapter != null && msg.arg2 > 0) {
                super@PageSvd.onLoaded(c.mm.saved?.items.isNullOrEmpty(), false)
                b.rv.adapter?.notifyItemRangeInserted(msg.arg1, msg.arg2)
                //c.bnvBadge(1, c.mm.saved?.total_count?.toInt() ?: 0)
            } else onLoaded(c.mm.saved?.items.isNullOrEmpty())

            if (c.mm.saved?.more_available == true && !b.rv.canScrollVertically(1)
                && thread?.active != true
            ) thread = FetchSome().also { it.start() }
        },
        HANDLE_ABORTED to { onFailed(c.getString(R.string.loadFailed)) },
        Api.HANDLE_ERROR to {
            onFailed(
                c.getString(R.string.unknownError, "${(it.obj as? NetworkResponse)?.statusCode}")
            )
        },
        Expandable.HANDLE_EXPANDABLE_ERROR to {
            UiTools.snackbar(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG, c.b.bnv)
        },
        HANDLE_UNSAVE_DONE to { msg ->
            //c.mm.saved?.apply { if (total_count != null && total_count > 0.0) total_count -= 1.0 }
            //c.bnvBadge(1, c.mm.saved?.total_count?.toInt() ?: 0)
            c.mm.saved?.items?.find { it.media.id == msg.obj as String }?.let { media ->
                val x = c.mm.saved!!.items!!.indexOf(media)
                c.mm.saved!!.items!!.removeAt(x)
                b.rv.adapter?.notifyItemRemoved(x)
                b.rv.adapter?.notifyItemRangeChanged(x, c.mm.saved!!.items!!.size)
                if (c.mm.saved?.items.isNullOrEmpty()) onLoaded(true)
            }
        },
        HANDLE_INIT_QUEUER to { Downloads.initService(c, "") },
    )
    override var tracker: SelectionTracker<String>? = null
    override var selectivity = false

    companion object : PageCompanion() {
        const val HANDLE_UNSAVE_DONE = 10
        const val HANDLE_INIT_QUEUER = 11
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
                if (!b.rv.canScrollVertically(1) &&
                    thread?.active != true && c.mm.saved?.more_available != false
                ) thread = FetchSome().also { it.start() }
            }
        })
        b.rv.layoutManager = object : SafeGridManager(c, 3) {
            override fun canScrollVertically(): Boolean =
                super.canScrollVertically() && selectionGuide == null
        }

        if (c.mm.saved != null) onLoaded(c.mm.saved?.items.isNullOrEmpty())
        else if (thread?.active != true) thread = FetchSome().also { it.start() }
    }

    override fun onStart() {
        super.onStart()
        if (bInitialised && b.rv.adapter != null && ftDetached) buildSelection()
    }

    override fun onRefresh() {
        if (thread?.active == true) return
        c.mm.saved = null
        b.rv.adapter?.notifyDataSetChanged()
        b.empty.vis(false)
        tracker?.clearSelection()
        thread = FetchSome().also { it.start() }
    }

    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
        //if (!asGuest) c.bnvBadge(1, c.mm.saved?.total_count?.toInt() ?: 0)

        if (b.rv.adapter == null) b.rv.adapter = ListSvd(c, this)
        else b.rv.adapter?.notifyDataSetChanged()

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
            translationY = c.dm.widthPixels * -0.01f // TODO both cause inconsistencies
            b.root.addView(this, 1)
        }

        if (tracker == null) buildSelection()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mtUnsaveDownload -> {
                if (tracker != null && c.mm.saved != null && saver?.active != true) saver = Saver(
                    c, this, tracker!!.selection, unsave = true, download = true
                ).also { it.start() }
                tracker?.clearSelection()
            }
            R.id.mtDownload -> {
                if (tracker != null && c.mm.saved != null && saver?.active != true) saver = Saver(
                    c, this, tracker!!.selection, unsave = false, download = true
                ).also { it.start() }
                tracker?.clearSelection()
            }
            R.id.mtUnsave -> {
                if (tracker != null && c.mm.saved != null && saver?.active != true) saver = Saver(
                    c, this, tracker!!.selection, unsave = true, download = false
                ).also { it.start() }
                tracker?.clearSelection()
            }
            R.id.mtSelectAll -> if (c.mm.saved?.items != null)
                tracker?.setItemsSelected(c.mm.saved!!.items!!.map { it.media.id }, true)
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
            rv()!!.computeVerticalScrollOffset() > 0 && (b.rv.adapter as ListSvd?)?.expandable?.zoomed != true
        )
    }

    override fun goBack(): Boolean {
        if (bInitialised) (b.rv.adapter as ListSvd?)?.also {
            if (it.expandable.zoomed) {
                jumper()?.vis(true)
                it.expandable.collapse(); return@goBack true; }
        }
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return true
        }
        return false
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.mm.saved?.items?.getOrNull(i)?.media?.id
        override fun getPosition(key: String): Int {
            c.mm.saved?.items?.forEachIndexed { i, item ->
                if (item.media.id == key) return@getPosition i
            }
            return -1
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    inner class SelectObserver : SelectionTracker.SelectionObserver<String>() {
        override fun onItemStateChanged(key: String, selected: Boolean) {
            if (c.tbTitle == null) return
            BadgeUtils.detachBadgeDrawable(c.selectionBadge, c.tbTitle!!)
            if (c.tbTitle?.parent == null) return
            // to avoid NullPointerException in BadgeDrawable.updateAnchorParentToNotClip
            BadgeUtils.attachBadgeDrawable(
                BadgeDrawable.create(ContextThemeWrapper(c, UiTools.materialTheme)).apply {
                    number = tracker?.selection?.size() ?: 0
                    backgroundColor = c.ca[1]
                    badgeTextColor = if (c.night()) c.bg[1] else c.color(R.color.defBG)
                    c.selectionBadge = this
                    maxCharacterCount = UiTools.MAX_BADGE_CHAR
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
            if (status) {
                (b.rv.adapter as ListSvd?)?.firstLongClickSelect = true
                if (selectionGuide != null) {
                    b.root.removeView(selectionGuide)
                    c.gsp.edit { putBoolean(Settings.spLearntSelection, true) }
                    b.rv.suppressLayout(false)
                }
            } else {
                BadgeUtils.detachBadgeDrawable(c.selectionBadge, c.tbTitle!!)
                c.selectionBadge = null
            }
        }
    }

    inner class FetchSome : BaseThread() {
        override fun run() {
            if (c.m.acc == null || c.mm.saved?.more_available == false) return
            super.run()
            reqQueue.adder = Api<Media.SavedWrapper>(
                c, Api.Endpoint.SAVED.url + (c.mm.saved?.next_max_id?.let { "?max_id=$it" } ?: ""),
                Media.SavedWrapper::class, handler, autoQueue = false, onError = { interrupt() }
            ) { wrapper ->
                if (!active) return@Api
                if (wrapper.items == null) {
                    handler?.obtainMessage(HANDLE_ABORTED)?.sendToTarget()
                    interrupt(); return@Api; }
                if (c.mm.saved == null) {
                    c.mm.saved = wrapper
                    handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                } else c.mm.saved?.apply {
                    val lastBefore = items!!.size
                    items!!.addAll(wrapper.items!!)
                    more_available = wrapper.more_available
                    next_max_id = wrapper.next_max_id
                    num_results = wrapper.num_results
                    handler?.obtainMessage(HANDLE_FETCHED, lastBefore, wrapper.items!!.size)
                        ?.sendToTarget()
                }
                interrupt()
            }
        }
    }

    class Saver(
        c: Main, val f: PageSvd,
        selection: Selection<String>, private val unsave: Boolean, private val download: Boolean
    ) : BaseSaver<Main>(c, selection) {
        companion object : Alive.OfThread()

        override val com: Alive.OfThread = Companion

        override fun handle() {
            val svd = list.getOrNull(0)
            if (svd == null) {
                if (download) handler?.obtainMessage(HANDLE_INIT_QUEUER)?.sendToTarget()
                interrupt()
                return; }
            val saved = (c as Main).mm.saved?.items?.find { it.media.id == svd }
            if (saved == null) {
                ended(); return; }

            if (download) try {
                saved.media.queue(c.dao)
            } catch (e: IllegalStateException) { // DB is closed
            }
            if (unsave) f.reqQueue.adder = Api<Rest>(
                c, Api.Endpoint.UNSAVE.url.format(saved.media.id), Rest::class, null,
                method = Request.Method.POST, autoQueue = false, onError = { ended() }
            ) { rest ->
                if (rest.status == "ok") {
                    handler?.obtainMessage(HANDLE_UNSAVE_DONE, svd)?.sendToTarget()
                    c.incrementCounter(Settings.spUnsaveCount)
                }
                ended()
            }
            if (!unsave) ended()
        }
    }
}
