package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageSvdBinding
import ir.mahdiparastesh.instatools.job.CommandService
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.util.*
import ir.mahdiparastesh.instatools.util.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.SafeGridManager
import ir.mahdiparastesh.instatools.view.Selective
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.shake
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageSvd : BasePageMain(BaseActivity.Theme.SECONDARY), Selective {
    lateinit var b: PageSvdBinding
    private val pickle: Pickle by lazy {
        Pickle(c.cacheDir, c.m.acc!!.id, Pickle.Type.SAVED, null)
    }
    val downloadsPickle: Pickle by lazy {
        Pickle(c.filesDir, c.m.acc!!.id, Pickle.Type.DOWNLOAD_LIST, null)
    }
    private var selectionGuide: LottieAnimationView? = null

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
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
            withContext(Dispatchers.Main) { onLoaded() }
            return; }

        // fetch online saved posts
        val cursor = if (!reset) (c.mm.saved?.next_max_id?.let { "?max_id=$it" } ?: "") else ""
        val lazyList = Api.json<Rest.LazyList<Rest.SavedItem>>(Api.Endpoint.SAVED.url + cursor)

        // update the data model and the UI
        if (c.mm.saved == null || reset) {
            c.mm.saved = lazyList
            withContext(Dispatchers.Main) { onLoaded() }
        } else c.mm.saved?.apply {
            val lastBefore = items.size
            items.addAll(lazyList.items)
            more_available = lazyList.more_available
            next_max_id = lazyList.next_max_id
            withContext(Dispatchers.Main) {
                onLazilyLoaded(lastBefore, lazyList.items.size)
            }
        }

        // cache the data model
        c.mm.saved?.also { pickle.save(it) }
    }

    override fun onLoaded() {
        super.onLoaded()
        if (!canLoadMore()) c.mm.savedCount.value = c.mm.saved?.items?.size ?: 0

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

    override fun onLazilyLoaded(start: Int, size: Int) {
        super.onLazilyLoaded(start, size)
        if (!canLoadMore()) c.mm.savedCount.value = c.mm.saved?.items?.size ?: 0
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mtUnsaveDownload ->
                processMedia(unsave = true, download = true)
            R.id.mtDownload ->
                processMedia(unsave = false, download = true)
            R.id.mtUnsave ->
                processMedia(unsave = true, download = false)
            R.id.mtSelectAll -> if (c.mm.saved != null)
                tracker?.setItemsSelected(c.mm.saved!!.items.map { it.media.id() }, true)

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

    fun processMedia(download: Boolean, unsave: Boolean) {
        val selection = tracker?.selection ?: return
        val saved = c.mm.saved ?: return
        val deletion = arrayListOf<Int>()
        CoroutineScope(Dispatchers.Default).launch {
            for (svd in saved.items.indices) {
                if (saved.items[svd].media.id() !in selection) continue
                if (download) c.m.downloads.addAll(saved.items[svd].media.queue())
                if (unsave) {
                    c.m.commands.add(
                        Command(saved.items[svd].media, unsave = true)
                    )
                    deletion.add(svd)
                    c.incrementCounter(Settings.spUnsaveCount)
                }
            }
            if (download) c.m.downloads.pickle(downloadsPickle)
            if (unsave) c.startService(
                Intent(c, CommandService::class.java).setAction(ForegroundService.ACTION_START)
            )
            withContext(Dispatchers.Main) {
                tracker?.clearSelection()
                if (unsave) {
                    var errored = false
                    for (del in deletion.reversed()) {
                        try {
                            c.mm.saved?.items?.removeAt(del)
                            b.rv.adapter?.notifyItemRemoved(del)
                            b.rv.adapter?.notifyItemRangeChanged(del, saved.items.size)
                            c.mm.savedCount.value = c.mm.savedCount.value?.let { it - 1 }
                        } catch (_: IndexOutOfBoundsException) {
                            errored = true
                        }
                    }
                    if (!errored) pickle.save(saved)
                }
            }
        }
    }

    override fun goBack(): Boolean {
        (b.rv.adapter as? ListSvd)?.also {
            if (it.expandable.zoomed) {
                b.jumper.vis(true)
                it.expandable.collapse(); return@goBack true; }
        }
        if (tracker?.hasSelection() == true) {
            if (tracker!!.selection.size() < 3)
                tracker?.clearSelection()
            else {
                MaterialAlertDialogBuilder(
                    android.view.ContextThemeWrapper(c, R.style.Theme_InstaTools_Dialog_Secondary)
                ).apply {
                    setTitle(R.string.deselectAll)
                    setMessage(R.string.deselectAllSure)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ -> tracker?.clearSelection() }
                }.show()
            }
            return true
        }
        return false
    }

    inner class PostKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String? = c.mm.saved?.items?.getOrNull(i)?.media?.id()
        override fun getPosition(key: String): Int {
            c.mm.saved?.items?.forEachIndexed { i, item ->
                if (item.media.id() == key) return@getPosition i
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
                BadgeDrawable.create(
                    androidx.appcompat.view.ContextThemeWrapper(c, UiTools.materialTheme)
                ).apply {
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
}
