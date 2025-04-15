package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageSvdBinding
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BasePageMain
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.OnlineLister
import ir.mahdiparastesh.instatools.view.PostSelector
import ir.mahdiparastesh.instatools.view.SafeGridManager
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.shake
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageSvd : BasePageMain(BaseActivity.Theme.SECONDARY), OnlineLister, PostSelector {
    lateinit var b: PageSvdBinding
    private val pickle: Pickle by lazy {
        Pickle(c.cacheDir, c.c.acc!!.id, Pickle.Type.SAVED, null)
    }
    private var selectionGuide: LottieAnimationView? = null

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override var job: Job? = null
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val emptyIcon: Int = R.drawable.done_svd
    override val expandable: Expandable by lazy {
        Expandable(c, b.expanded, c.color(if (!c.night) R.color.defBG else R.color.CS)) {
            updateShadow()
            updateJumper()
        }
    }
    override val selectiveMenuRes: Int = R.menu.main_tlb_svd_select
    override var tracker: SelectionTracker<Long>? = null
    override var selectivity = false
    override val dialogContext: Context
        get() = ContextThemeWrapper(c, R.style.Theme_InstaTools_Secondary)

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.vm.saved != null
    override fun isModelEmpty(): Boolean = c.vm.saved?.items?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListSvd(c, this)
    override fun canLoadMore(): Boolean = c.vm.saved?.more_available != false

    companion object {
        var handler: Handler? = null
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageSvdBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // list
        b.rv.layoutManager = object : SafeGridManager(c, 3) {
            override fun canScrollVertically(): Boolean =
                super.canScrollVertically() && selectionGuide == null
        }

        // handler
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    ForegroundService.HANDLE_ITEM_UPDATED -> {
                        val id = msg.obj as Long
                        var index = -1
                        c.vm.saved?.items?.forEachIndexed { i, svd ->
                            if (svd.media.uid == id) {
                                Command.applyChangesToMedia(svd.media, msg.arg1)
                                index = i
                                return@forEachIndexed; }
                        }
                        if (index == -1) return
                        b.rv.adapter?.notifyItemChanged(index)

                        CoroutineScope(Dispatchers.IO).launch {
                            c.vm.saved?.also { pickle.save(it) }
                        }
                    }
                }
            }
        }
    }

    override fun canRefresh(): Boolean =
        super.canRefresh() && selectionGuide == null

    override suspend fun fetch(reset: Boolean) {
        // first read from cache if available
        val cache =
            if (c.vm.saved == null && !reset) pickle.restore<Rest.LazyList<Rest.SavedItem>>()
            else null
        if (cache != null) {
            c.vm.saved = cache
            withContext(Dispatchers.Main) { onLoaded() }
            return; }

        // fetch online saved posts
        val cursor = if (!reset) (c.vm.saved?.next_max_id?.let { "?max_id=$it" } ?: "") else ""
        val lazyList = Api.json<Rest.LazyList<Rest.SavedItem>>(Api.Endpoint.SAVED.url + cursor)

        // update the data model and the UI
        if (c.vm.saved == null || reset) {
            c.vm.saved = lazyList
            withContext(Dispatchers.Main) { onLoaded() }
        } else c.vm.saved?.apply {
            val lastBefore = items.size
            items.addAll(lazyList.items)
            more_available = lazyList.more_available
            next_max_id = lazyList.next_max_id
            withContext(Dispatchers.Main) {
                onLazilyLoaded(lastBefore, lazyList.items.size)
            }
        }

        // cache the data model
        c.vm.saved?.also { pickle.save(it) }
    }

    override fun onLoaded() {
        super<OnlineLister>.onLoaded()
        //if (!canLoadMore()) c.vm.savedCount.value = c.vm.saved?.items?.size ?: 0

        // teach the user how to select items
        if (!isModelEmpty() && !c.c.gsp.getBoolean(Settings.spLearntSelection, false)
            && selectionGuide == null
        ) selectionGuide = LottieAnimationView(c).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topToTop = ConstraintLayout.LayoutParams.PARENT_ID }
            repeatCount = LottieDrawable.INFINITE
            setAnimation(R.raw.guide_selection)
            playAnimation()
            translationX = c.dm.widthPixels * -0.12f
            translationY = c.dm.widthPixels * -0.01f
            b.root.addView(this, 1)
        }
    }

    override fun onLazilyLoaded(start: Int, size: Int) {
        super.onLazilyLoaded(start, size)
        //if (!canLoadMore()) c.vm.savedCount.value = c.vm.saved?.items?.size ?: 0
    }

    override fun selectionKeyProvider() = object : ItemKeyProvider<Long>(SCOPE_MAPPED) {
        override fun getKey(i: Int): Long? = c.vm.saved?.items?.getOrNull(i)?.media?.uid
        override fun getPosition(key: Long): Int {
            c.vm.saved?.items?.forEachIndexed { i, item ->
                if (item.media.uid == key) return@getPosition i
            }
            return -1
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun selectionObserver() = object : SelectionTracker.SelectionObserver<Long>() {
        override fun onItemStateChanged(key: Long, selected: Boolean) {
            if (c.tbTitle == null) return
            BadgeUtils.detachBadgeDrawable(c.selectionBadge, c.tbTitle!!)
            if (c.tbTitle?.parent == null) return
            // to avoid NullPointerException in BadgeDrawable.updateAnchorParentToNotClip
            BadgeUtils.attachBadgeDrawable(
                BadgeDrawable.create(ContextThemeWrapper(c, UiTools.materialTheme)).apply {
                    number = tracker?.selection?.size() ?: 0
                    backgroundColor = c.ca[1]
                    badgeTextColor = if (c.night) c.bg[1] else c.color(R.color.defBG)
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
                    c.c.gsp.edit { putBoolean(Settings.spLearntSelection, true) }
                    b.rv.suppressLayout(false)
                }
            } else {
                BadgeUtils.detachBadgeDrawable(c.selectionBadge, c.tbTitle!!)
                c.selectionBadge = null
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mtUnsaveDownload ->
                enqueueSelectedMedia(
                    c, c.vm.saved, unsave = true, download = true, onDeleteItems = ::onDeleteItems
                )
            R.id.mtDownload ->
                enqueueSelectedMedia(c, c.vm.saved, download = true)
            R.id.mtUnsave ->
                enqueueSelectedMedia(c, c.vm.saved, unsave = true, onDeleteItems = ::onDeleteItems)
            R.id.mtLike ->
                enqueueSelectedMedia(c, c.vm.saved, like = true)
            R.id.mtUnlike ->
                enqueueSelectedMedia(c, c.vm.saved, unlike = true)

            R.id.mtSelectAll -> if (c.vm.saved != null)
                tracker?.setItemsSelected(c.vm.saved!!.items.map { it.media.uid }, true)
            R.id.mtDeselectAll ->
                tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    private fun onDeleteItems(deletion: List<Int>) {
        c.c.incrementCounter(Settings.spUnsaveCount, deletion.size.toLong())
        var errored = false
        for (del in deletion.reversed()) try {
            c.vm.saved?.items?.removeAt(del)
            b.rv.adapter?.notifyItemRemoved(del)
            //c.vm.savedCount.value = c.vm.savedCount.value?.let { it - 1 }
            onListResized()
        } catch (_: IndexOutOfBoundsException) {
            errored = true
        }
        if (!errored) c.vm.saved?.also { pickle.save(it) }
    }

    override fun goBack(): Boolean {
        (b.rv.adapter as? ListSvd)?.also {
            if (it.expandable.zoomed) {
                b.jumper.vis(true)
                it.expandable.collapse(); return@goBack true; }
        }
        return onGoBackWithSelection()
    }

    override fun onDestroy() {
        handler = null
        super.onDestroy()
    }
}
