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
import com.airbnb.lottie.LottieDrawable
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("NotifyDataSetChanged")
class PageSvd : BasePageMain(), Selective {
    lateinit var b: PageSvdBinding
    var thread: Job? = null
    var saver: Saver? = null
    private var selectionGuide: LottieAnimationView? = null
    private var reallyHasMore = true

    override val com: PageCompanion = Companion
    override val theme: BaseActivity.Theme = BaseActivity.Theme.SECONDARY
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val emptyIcon: Int = R.drawable.done_svd
    override fun expanded(): ExpandableBinding = b.expanded
    override val selectiveMenuRes: Int = R.menu.main_tlb_svd_select

    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        Expandable.HANDLE_EXPANDABLE_ERROR to {
            UiTools.snackbar(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG, c.b.bnv)
        },
    )
    override var tracker: SelectionTracker<String>? = null
    override var selectivity = false

    companion object : PageCompanion()

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
                if (!b.rv.canScrollVertically(1) && reallyHasMore)
                    fetchSome()
            }
        })
        b.rv.layoutManager = object : SafeGridManager(c, 3) {
            override fun canScrollVertically(): Boolean =
                super.canScrollVertically() && selectionGuide == null
        }

        if (c.mm.saved != null) onLoaded(c.mm.saved?.items.isNullOrEmpty())
        else fetchSome()
    }

    override fun onStart() {
        super.onStart()
        if (bInitialised && b.rv.adapter != null && ftDetached) buildSelection()
    }

    override fun onRefresh() {
        if (thread != null) return
        c.mm.saved = null
        b.rv.adapter?.notifyDataSetChanged()
        b.empty.vis(false)
        tracker?.clearSelection()
        reallyHasMore = true
        fetchSome()
        c.updateProfile()
    }

    override fun onLoaded(isEmpty: Boolean) {
        super.onLoaded(isEmpty)

        if (b.rv.adapter == null) b.rv.adapter = ListSvd(c, this)
        else b.rv.adapter?.notifyDataSetChanged()

        // Selection Guide
        if (!Main.guest && !isEmpty && !c.gsp.getBoolean(Settings.spLearntSelection, false)
            && selectionGuide == null
        ) selectionGuide = LottieAnimationView(c).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topToTop = ConstraintLayout.LayoutParams.PARENT_ID }
            repeatCount = LottieDrawable.INFINITE
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
                if (tracker != null && c.mm.saved != null && saver?.active != true)
                    saver = Saver(tracker!!.selection, unsave = true, download = true)
                tracker?.clearSelection()
            }
            R.id.mtDownload -> {
                if (tracker != null && c.mm.saved != null && saver?.active != true)
                    saver = Saver(tracker!!.selection, unsave = false, download = true)
                tracker?.clearSelection()
            }
            R.id.mtUnsave -> {
                if (tracker != null && c.mm.saved != null && saver?.active != true)
                    saver = Saver(tracker!!.selection, unsave = true, download = false)
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

    fun fetchSome() {
        if (thread != null || c.mm.saved?.more_available == false) return
        thread = CoroutineScope(Dispatchers.IO).launch {
            val wrapper = Api.call<Media.SavedWrapper>(
                Api.Endpoint.SAVED.url + (c.mm.saved?.next_max_id?.let { "?max_id=$it" } ?: ""),
                Media.SavedWrapper::class,
                onError = { code -> onFailed(c.getString(R.string.unknownError, "$code")) }
            )
            if (wrapper == null) return@launch

            if (wrapper.items == null) {
                withContext(Dispatchers.Main) {
                    onFailed(c.getString(R.string.loadFailed))
                }
                return@launch; }

            // check if the user has hidden saves
            if (wrapper.items!!.isEmpty()) {
                if (c.mm.saved == null) c.mm.saved = wrapper
                reallyHasMore = false
                withContext(Dispatchers.Main) {
                    if (c.mm.saved?.items.isNullOrEmpty()) onLoaded(true)
                    c.mm.savedCount.value = c.mm.saved?.items?.size ?: 0
                }
                return@launch; }

            // update the data model
            var lastBefore: Int? = null
            if (c.mm.saved == null) {
                c.mm.saved = wrapper
            } else c.mm.saved?.apply {
                lastBefore = items!!.size
                items!!.addAll(wrapper.items!!)
                more_available = wrapper.more_available
                next_max_id = wrapper.next_max_id
            }

            withContext(Dispatchers.Main) {
                if (b.rv.adapter == null)
                    onLoaded(c.mm.saved?.items.isNullOrEmpty())
                else
                    b.rv.adapter?.notifyItemRangeInserted(lastBefore!!, wrapper.items!!.size)

                if (!b.rv.canScrollVertically(1))
                    fetchSome()

                if (c.mm.saved?.more_available == false)
                    c.mm.savedCount.value = c.mm.saved?.items?.size ?: 0
            }

            thread = null
        }
    }

    inner class Saver(
        selection: Selection<String>, private val unsave: Boolean, private val download: Boolean
    ) : SelectionHandler(selection) {

        override suspend fun handle() {
            val svd = next()
            if (svd == null) {
                if (download)
                    withContext(Dispatchers.Main) { Downloads.initService(c, "") }
                return; }
            val saved = c.mm.saved?.items?.find { it.media.id == svd } // TODO make it a HashMap
            if (saved == null) {
                ended(); return; }

            if (download) saved.media.queue(c.dao)

            if (!unsave) {
                ended()
                return; }

            val rest = Api.call<Rest>(
                Api.Endpoint.UNSAVE.url.format(saved.media.id), Rest::class, isPost = true,
                onError = { code ->
                    withContext(Dispatchers.Main) {
                        UiTools.snackbar(b.root, Api.error(code), Snackbar.LENGTH_LONG)
                    }
                }
            )
            if (rest?.status != "ok") return

            c.incrementCounter(Settings.spUnsaveCount)
            withContext(Dispatchers.Main) {
                //c.mm.saved?.apply { if (total_count != null && total_count > 0.0) total_count -= 1.0 }
                c.mm.savedCount.value = c.mm.savedCount.value?.let { it - 1 }
                c.mm.saved?.items?.find { it.media.id == svd }?.let { media ->
                    val x = c.mm.saved!!.items!!.indexOf(media)
                    c.mm.saved!!.items!!.removeAt(x)
                    b.rv.adapter?.notifyItemRemoved(x)
                    b.rv.adapter?.notifyItemRangeChanged(x, c.mm.saved!!.items!!.size)
                    if (c.mm.saved?.items.isNullOrEmpty()) onLoaded(true)
                }
            }
            if (size() > 1) {
                delay(500)
                ended()
            } else ended()
        }
    }
}
