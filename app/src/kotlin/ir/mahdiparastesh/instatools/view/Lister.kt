package ir.mahdiparastesh.instatools.view

import android.animation.ObjectAnimator
import android.view.View
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface Lister {
    val root: ConstraintLayout?
    val tbShadow: View?
    val expandable: Expandable?
    var shouldShowJumper: MutableLiveData<Boolean>
    var anJumper: ObjectAnimator?

    val rv: RecyclerView? get() = root?.findViewById(R.id.rv)
    val empty: AppCompatTextView? get() = root?.findViewById(R.id.empty)
    val jumper: ImageView? get() = root?.findViewById(R.id.jumper)

    fun isBInitialised(): Boolean
    fun shouldLoadOnPrepare(): Boolean = true
    fun isModelLoaded(): Boolean
    fun isModelEmpty(): Boolean
    fun createAdapter(): RecyclerView.Adapter<*>
    fun screenHeight(): Int

    fun prepareListing(c: BaseActivity) {

        // list
        setOnScrollListener()

        // jumper
        jumper?.apply {
            setOnClickListener { rv?.smoothScrollToPosition(0) }
            translationY = UiTools.jumperTrans(c)
        }
        shouldShowJumper.observe(c) {
            anJumper?.cancel()
            anJumper = jumper?.let { jumper -> UiTools.anJumper(c, jumper, it) }
        }

        if (shouldLoadOnPrepare()) {
            if (isModelEmpty()) onLoaded()
            else load()
        }
    }

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
        val condition = rv!!.computeVerticalScrollOffset() > screenHeight()
        if (condition != shouldShowJumper.value) shouldShowJumper.value = condition
    }

    @MainThread
    fun load(reset: Boolean = false)

    @MainThread
    fun onLoaded() {
        rv?.adapter = createAdapter()
        empty?.vis(isModelEmpty())
        if (this is Selective && tracker == null) buildSelection()
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
        refresher?.setOnClickListener { onRefresh() }
        refresher?.setOnChildScrollUpCallback { _, _ -> !canRefresh() }
    }

    override fun onScroll() {
        super.onScroll()
        if (rv?.canScrollVertically(1) == false && canLoadMore())
            load()
    }

    fun canRefresh(): Boolean =
        !rv!!.canScrollVertically(-1) && !(this is Selective && tracker?.hasSelection() == true)

    override fun load(reset: Boolean) {
        if (job?.isActive == true) return
        job = CoroutineScope(Dispatchers.IO).launch {
            fetch(reset)
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
        if (isModelEmpty() && canLoadMore() == true && !rv!!.canScrollVertically(1))
            load()
    }

    @MainThread
    fun onLazilyLoaded(start: Int, size: Int) {
        if (size > 0)
            rv?.adapter?.notifyItemRangeInserted(start, size)
        if (isModelEmpty() && canLoadMore() == true && !rv!!.canScrollVertically(1))
            load()
    }

    @MainThread
    fun onFailed(statusCode: Int) {
        refresher?.isRefreshing = false
        UiTools.snackbar(root!!, root!!.context.getString(Api.error(statusCode), statusCode))
        error?.vis()
        error?.setOnClickListener {
            refresher?.isRefreshing = true
            load()
        }
        if (loading != null && root!!.contains(loading!!)) {
            loading?.animation?.cancel()
            root?.removeView(loading)
        }
        empty?.vis(isModelEmpty())
    }

    @MainThread
    fun onLazilyFailed(statusCode: Int) {
        UiTools.snackbar(root!!, root!!.context.getString(Api.error(statusCode), statusCode))
    }

    override fun onRefresh() {
        load(true)
    }
}

/** Helper interface for selection mode of [RecyclerView]. */
interface Selective {
    var tracker: SelectionTracker<String>?
    var selectivity: Boolean

    fun buildSelection()
}
