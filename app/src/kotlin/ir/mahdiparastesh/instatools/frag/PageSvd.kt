package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
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
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageSvdBinding
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.util.*
import ir.mahdiparastesh.instatools.util.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.SafeGridManager
import ir.mahdiparastesh.instatools.view.SelectionHandler
import ir.mahdiparastesh.instatools.view.Selective
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.shake
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@SuppressLint("NotifyDataSetChanged")
class PageSvd : BasePageMain(BaseActivity.Theme.SECONDARY), Selective {
    lateinit var b: PageSvdBinding
    var saver: Saver? = null
    private val pickle: Pickle by lazy { Pickle(c.c, Pickle.Type.SAVED, null) }
    private var selectionGuide: LottieAnimationView? = null

    override val root: ConstraintLayout? get() = if (isBInitialised()) b.root else null
    override val emptyIcon: Int = R.drawable.done_svd
    override val expandable: Expandable by lazy {
        Expandable(
            c, b.expanded, c.color(if (!c.night()) R.color.defBG else R.color.CS)
        ) { updateShadow() }
    }
    override val selectiveMenuRes: Int = R.menu.main_tlb_svd_select
    override var tracker: SelectionTracker<String>? = null
    override var selectivity = false

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.mm.saved != null
    override fun isModelEmpty(): Boolean = c.mm.saved?.items?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListSvd(c, this)
    override fun canLoadMore(): Boolean = c.mm.saved?.more_available != false

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageSvdBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback tracker?.hasSelection() == true
                || selectionGuide != null
        }
        b.rv.layoutManager = object : SafeGridManager(c, 3) {
            override fun canScrollVertically(): Boolean =
                super.canScrollVertically() && selectionGuide == null
        }
    }

    override fun canRefresh(): Boolean =
        super.canRefresh() && selectionGuide == null

    override suspend fun fetch(reset: Boolean) {
        // first read from cache if available
        val cache =
            if (c.mm.saved == null && !reset) pickle.restore<Rest.LazyList<Rest.SavedItem>>()
            else null
        if (cache != null) {
            c.mm.saved = cache
            withContext(Dispatchers.Main) {
                onLoaded()
                if (c.mm.saved?.more_available == false)
                    c.mm.savedCount.value = c.mm.saved?.items?.size ?: 0
            }
            job = null
            return; }

        // fetch online saved posts
        val lazyList = Api.call<Rest.LazyList<Rest.SavedItem>>(
            Api.Endpoint.SAVED.url + (c.mm.saved?.next_max_id?.let { "?max_id=$it" } ?: ""),
            Rest.LazyList::class, generics = arrayOf(Rest.SavedItem::class),
            onError = { code -> if (c.mm.saved == null) onFailed(code) else onLazilyFailed(code) }
        )
        if (lazyList == null) {
            job = null
            return; }

        // update the data model
        var lastBefore: Int? = null
        if (c.mm.saved == null) {
            c.mm.saved = lazyList
        } else c.mm.saved?.apply {
            lastBefore = items.size
            items.addAll(lazyList.items)
            more_available = lazyList.more_available
            next_max_id = lazyList.next_max_id
        }

        // update the UI
        withContext(Dispatchers.Main) {
            if (lastBefore == null) onLoaded()
            else onLazilyLoaded(lastBefore, lazyList.items.size)
            if (c.mm.saved?.more_available == false)
                c.mm.savedCount.value = c.mm.saved?.items?.size ?: 0
        }

        // cache the data model
        c.mm.saved?.also { pickle.save(it) }

        job = null
    }

    override fun onLoaded() {
        super.onLoaded()

        // teach the user how to select items
        if (!isModelEmpty() && !c.gsp.getBoolean(Settings.spLearntSelection, false)
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
            R.id.mtSelectAll -> if (c.mm.saved != null)
                tracker?.setItemsSelected(c.mm.saved!!.items.map { it.media.pk() }, true)

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

    override fun goBack(): Boolean {
        (b.rv.adapter as? ListSvd)?.also {
            if (it.expandable.zoomed) {
                b.jumper.vis(true)
                it.expandable.collapse(); return@goBack true; }
        }
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return true
        }
        return false
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.mm.saved?.items?.getOrNull(i)?.media?.pk()
        override fun getPosition(key: String): Int {
            c.mm.saved?.items?.forEachIndexed { i, item ->
                if (item.media.pk() == key) return@getPosition i
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

    inner class Saver(
        selection: Selection<String>, private val unsave: Boolean, private val download: Boolean
    ) : SelectionHandler(selection) {

        override suspend fun handle() {
            val svd = next()
            if (svd == null) {
                if (download) Downloads.initService(c)
                if (unsave) c.mm.saved?.also { pickle.save(it) }
                return; }
            val saved = c.mm.saved?.items?.find { it.media.pk() == svd }
            if (saved == null) {
                ended(); return; }

            if (download) saved.media.queue(c.dao)

            if (!unsave) {
                ended()
                return; }

            val rest = Api.call<Rest.QuickResponse>(
                Api.Endpoint.UNSAVE.url.format(saved.media.pk()), Rest.QuickResponse::class,
                isPost = true, onError = { code ->
                    UiTools.snackbar(b.root, getString(Api.error(code), code))
                }
            )
            if (rest?.status != "ok") return

            c.incrementCounter(Settings.spUnsaveCount)
            withContext(Dispatchers.Main) {
                c.mm.savedCount.value = c.mm.savedCount.value?.let { it - 1 }
                c.mm.saved?.items?.find { it.media.pk() == svd }?.let { media ->
                    val x = c.mm.saved!!.items.indexOf(media)
                    c.mm.saved!!.items.removeAt(x)
                    b.rv.adapter?.notifyItemRemoved(x)
                    b.rv.adapter?.notifyItemRangeChanged(x, c.mm.saved!!.items.size)
                }
            }
            if (size() > 1) {
                delay(500)
                ended()
            } else ended()
        }
    }
}
