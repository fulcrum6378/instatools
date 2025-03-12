package ir.mahdiparastesh.instatools.view

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.MenuItem
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StableIdKeyProvider
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.job.CommandService
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface Lister {
    val root: ConstraintLayout?
    val tbShadow: View?
    val expandable: Expandable?
    var shouldShowJumper: Boolean
    var anJumper: ObjectAnimator?

    val rv: RecyclerView?
    val empty: View?
    val jumper: ImageView?

    fun isBInitialised(): Boolean
    fun shouldLoadOnPrepare(): Boolean = true
    fun isModelLoaded(): Boolean
    fun isModelEmpty(): Boolean
    fun createAdapter(): RecyclerView.Adapter<*>
    fun reuseAdapter(): Boolean = true
    fun screenHeight(): Int

    fun prepareListing(c: BaseActivity) {

        // list
        rv?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onScroll()
            }
        })

        // jumper
        jumper?.apply {
            setOnClickListener { rv?.smoothScrollToPosition(0) }
            translationY = jumperTrans(c)
        }

        if (shouldLoadOnPrepare()) {
            if (isModelLoaded()) onLoaded()
            else load()
        }
    }

    private fun jumperTrans(c: Context) =
        (c.resources.getDimension(R.dimen.jumperSize) +
            c.resources.getDimension(R.dimen.jumperBottom)) * 1.25f

    fun onScroll() {
        if (isBInitialised()) {
            updateShadow()
            updateJumper()
        }
    }

    fun updateShadow() {
        tbShadow?.vish(rv!!.computeVerticalScrollOffset() > 0 && expandable?.zoomed != true)
    }

    fun updateJumper() {
        val condition = shouldShowJumper()
        if (condition != shouldShowJumper) {
            shouldShowJumper = condition
            anJumper?.cancel()
            anJumper = jumper?.let { jumper ->
                ObjectAnimator.ofFloat(
                    jumper, View.TRANSLATION_Y, if (condition) 0f else jumperTrans(jumper.context)
                ).apply {
                    duration = 500L
                    interpolator = OvershootInterpolator(1.75f)
                    start()
                }
            }
        }
    }

    fun shouldShowJumper(): Boolean =
        rv!!.computeVerticalScrollOffset() > screenHeight() && expandable?.zoomed != true

    @MainThread
    fun load(reset: Boolean = false)

    @SuppressLint("NotifyDataSetChanged")
    @MainThread
    fun onLoaded() {
        if (rv == null) return
        if (rv!!.adapter == null || !reuseAdapter()) rv!!.adapter = createAdapter()
        else rv!!.adapter!!.notifyDataSetChanged()

        onListResized()
        if (this is PostSelector && tracker == null) buildSelection()
    }

    fun onListResized() {
        empty?.vis(isModelEmpty())
    }
}

interface OnlineLister : Lister, SwipeRefreshLayout.OnRefreshListener {
    var job: Job?
    val loading: LottieAnimationView? get() = root?.findViewById(R.id.loading)
    val error: ImageView? get() = root?.findViewById(R.id.error)
    val refresher: SwipeRefreshLayout? get() = root?.findViewById(R.id.refresher)

    fun canLoadMore(): Boolean

    override fun prepareListing(c: BaseActivity) {
        super.prepareListing(c)
        refresher?.setOnRefreshListener(this)
        refresher?.setOnClickListener { onRefresh() }
        refresher?.setOnChildScrollUpCallback { _, _ -> !canRefresh() }
    }

    override fun onScroll() {
        super.onScroll()
        if (hasReachedBottom()) load()
    }

    fun hasReachedBottom() = !rv!!.canScrollVertically(1)

    fun canRefresh(): Boolean =
        !rv!!.canScrollVertically(-1) && !(this is PostSelector && tracker?.hasSelection() == true)

    override fun load(reset: Boolean) {
        if ((!canLoadMore() && !reset) || job?.isActive == true) return
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                fetch(reset)
            } catch (e: Api.FailureException) {
                withContext(Dispatchers.Main) {
                    if (!isModelLoaded()) onFailed(e.code)
                    else onLazilyFailed(e.code) // or reset
                }
            }
        }.also {
            it.invokeOnCompletion { job = null }
        }
    }

    @WorkerThread
    suspend fun fetch(reset: Boolean)

    override fun onLoaded() {
        super.onLoaded()
        refresher?.isRefreshing = false
        if (loading != null && root!!.contains(loading!!)) {
            loading?.animation?.cancel()
            root!!.removeView(loading)
        }
        error?.vis(false)
        if (isModelEmpty() && hasReachedBottom()) load()
    }

    @MainThread
    fun onLazilyLoaded(start: Int, size: Int) {
        if (size > 0) rv?.adapter?.notifyItemRangeInserted(start, size)
        if (isModelEmpty() && hasReachedBottom()) load()
    }

    @MainThread
    fun onFailed(statusCode: Int) {
        refresher?.isRefreshing = false
        UiTools.snackbar(
            root!!, UiTools.apiError(root!!.context.applicationContext as InstaTools, statusCode)
        )
        error?.vis()
        error?.setOnClickListener {
            refresher?.isRefreshing = true
            load()
        }
        if (loading != null && root!!.contains(loading!!)) {
            loading?.animation?.cancel()
            root?.removeView(loading)
        }
        onListResized()
    }

    @MainThread
    fun onLazilyFailed(statusCode: Int) {
        refresher?.isRefreshing = false // in case of a refresh
        UiTools.snackbar(
            root!!, UiTools.apiError(root!!.context.applicationContext as InstaTools, statusCode)
        )
    }

    override fun onRefresh() {
        load(true)
    }
}

/**
 * Observes the lifecycle a [ir.mahdiparastesh.instatools.util.ForegroundService]
 * and switches start and stop buttons accordingly.
 */
interface ServiceOwner : Lister {
    val serviceActive: MutableLiveData<Boolean>
    val controller: MenuItem?

    @SuppressLint("NotifyDataSetChanged")
    override fun prepareListing(c: BaseActivity) {
        super.prepareListing(c)

        Delay(500) {
            serviceActive.observe(c) { bb ->
                controller?.apply {
                    setIcon(if (bb) R.drawable.pause else R.drawable.play)
                    setTitle(if (bb) R.string.stop else R.string.start)
                }
                rv?.adapter?.notifyDataSetChanged()
                onListResized()
            }
        }
    }

    override fun onListResized() {
        super.onListResized()
        controller?.isEnabled = !isModelEmpty()
    }
}

/** Helper interface for selection mode of [RecyclerView]. */
interface PostSelector : Lister {
    var tracker: SelectionTracker<Long>?
    var selectivity: Boolean
    val dialogContext: Context

    fun buildSelection() {
        // created only once, except after FragmentTransaction::attach()
        tracker = SelectionTracker.Builder(
            this::class.java.simpleName,
            rv!!,
            selectionKeyProvider(),
            ListPost.PostDetailsLookup(rv!!),
            StorageStrategy.createLongStorage()
        ).build().also { tracker ->
            selectionObserver()?.also { observer ->
                tracker.addObserver(observer)
            }
        }
    }

    fun selectionKeyProvider(): ItemKeyProvider<Long> =
        StableIdKeyProvider(rv!!)

    fun selectionObserver(): SelectionTracker.SelectionObserver<Long>?

    fun enqueueSelectedMedia(
        c: BaseActivity,
        dataModel: Any?,
        download: Boolean = false,
        save: Boolean = false,
        unsave: Boolean = false,
        like: Boolean = false,
        unlike: Boolean = false,
        onDeleteItems: ((indices: List<Int>) -> Unit)? = null,
    ) {
        val selection = tracker?.selection ?: return
        if (dataModel == null) return

        CoroutineScope(Dispatchers.Default).launch {

            // keep a list of deleted items only if needed
            var deletion: ArrayList<Int>? = null
            if (onDeleteItems != null) deletion = arrayListOf<Int>()

            // determine how to deal with the data model
            val indices: IntRange
            val getter: (Int) -> Media
            when (dataModel) {
                is Rest.LazyList<*> -> { // Rest.SavedItem
                    indices = dataModel.items.indices
                    getter = {
                        (dataModel.items[it] as Rest.SavedItem).media
                    }
                }
                is GraphQl.Page<*> -> { // Media
                    indices = dataModel.edges.indices
                    getter = {
                        @Suppress("UNCHECKED_CAST")
                        (dataModel.edges[it] as GraphQl.Edge<Media>).node
                    }
                }
                else ->
                    throw IllegalStateException()
            }

            // enqueue
            for (pos in indices) {
                val med = getter(pos)
                if (med.uid !in selection) continue
                if (download) c.c.downloads.addAll<Download>(med.queue(), false)
                if (save || unsave || like || unlike) {
                    c.c.commands.add<Command>(
                        Command(
                            med,
                            save = save,
                            unsave = unsave,
                            like = like,
                            unlike = unlike,
                        ),
                        false
                    )
                    deletion?.add(pos)
                }
            }

            // start the Services
            if (download) {
                c.c.downloads.save<Download>()
                Downloads.initService(c)
            }
            if (save || unsave || like || unlike) {
                c.c.commands.save<Command>()
                c.startService(
                    Intent(c, CommandService::class.java).setAction(ForegroundService.ACTION_START)
                )
            }

            // handle the UI
            withContext(Dispatchers.Main) {
                tracker?.clearSelection()
                onDeleteItems?.also { it(deletion!!) }
            }
        }
    }

    fun onGoBackWithSelection(): Boolean {
        if (tracker?.hasSelection() == true) {
            if (tracker!!.selection.size() < UiTools.DESELECT_MIN_ASK)
                tracker?.clearSelection()
            else {
                MaterialAlertDialogBuilder(dialogContext).apply {
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
}

interface Counter {
    var countBadge: BadgeDrawable?

    @SuppressLint("UnsafeOptInUsageError")
    fun updateCount(c: BaseActivity, n: Int) {
        BadgeUtils.detachBadgeDrawable(countBadge, c.tbTitle!!)
        BadgeUtils.attachBadgeDrawable(
            BadgeDrawable.create(ContextThemeWrapper(c, UiTools.materialTheme)).apply {
                number = n
                backgroundColor = c.themeColor(android.R.attr.colorAccent)
                badgeTextColor =
                    if (c.night()) c.themeColor(android.R.attr.colorPrimary)
                    else c.color(R.color.defBG)
                countBadge = this
                maxCharacterCount = UiTools.MAX_BADGE_CHAR
            }, c.tbTitle!!
        )
    }
}
