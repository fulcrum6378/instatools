package ir.mahdiparastesh.instatools.view

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.util.Delay
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
        setOnScrollListener()

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

    fun setOnScrollListener() {
        rv?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onScroll()
            }
        })
    }

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
        if (this is Selective && tracker == null) buildSelection()
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
        !rv!!.canScrollVertically(-1) && !(this is Selective && tracker?.hasSelection() == true)

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
        UiTools.snackbar(root!!, UiTools.apiError(root!!.context, statusCode))
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
        UiTools.snackbar(root!!, UiTools.apiError(root!!.context, statusCode))
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
interface Selective {
    var tracker: SelectionTracker<String>?
    var selectivity: Boolean

    fun buildSelection()
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
