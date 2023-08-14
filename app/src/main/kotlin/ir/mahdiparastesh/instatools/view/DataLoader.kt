package ir.mahdiparastesh.instatools.view

import android.animation.ObjectAnimator
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.view.UiTools.vis

interface DataLoader {
    val root: ConstraintLayout?
    val bInitialised: Boolean
    var shouldShowJumper: MutableLiveData<Boolean>
    var anJumper: ObjectAnimator?
    val heightPixels: Int // always make it lazy everywhere

    fun rv(): RecyclerView? = root?.findViewById(R.id.rv)
    fun empty(): AppCompatTextView? = root?.findViewById(R.id.empty)
    fun jumper(): ImageView? = root?.findViewById(R.id.jumper)

    fun prepareListing(c: BaseActivity) {
        shouldShowJumper = MutableLiveData(false)
        anJumper = null
        rv()?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onRecyclerViewScrolled()
            }
        })
        jumper()?.apply {
            setOnClickListener { rv()?.smoothScrollToPosition(0) }
            translationY = UiTools.jumperTrans(c)
        }
        shouldShowJumper.observe(c) {
            anJumper?.cancel()
            anJumper = jumper()?.let { jumper -> UiTools.anJumper(c, jumper, it) }
        }
    }

    fun updateShadow()

    fun onRecyclerViewScrolled() {
        updateJumper()
    }

    fun updateJumper() {
        if (bInitialised) (rv()!!.computeVerticalScrollOffset() > heightPixels)
            .apply { if (this != shouldShowJumper.value) shouldShowJumper.value = this }
    }
}

interface OnlineDataLoader : DataLoader {
    fun loading(): LottieAnimationView? = root?.findViewById(R.id.loading)
    fun error(): ImageView? = root?.findViewById(R.id.error)

    fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        if (loading() != null && root!!.contains(loading()!!)) {
            loading()?.animation?.cancel()
            root!!.removeView(loading())
        }
        error()?.vis(false)
        emptied(isEmpty)
    }

    fun onFailed(message: String) {
        if (root != null)
            UiTools.snackbar(root!!, message, Snackbar.LENGTH_LONG)
        if (loading() != null && root!!.contains(loading()!!)) {
            loading()?.animation?.cancel()
            root?.removeView(loading())
        }
        if (rv()?.adapter == null) error()?.vis()
        emptied(false)
    }

    fun emptied(isEmpty: Boolean) {
        empty()?.vis(isEmpty)
    }
}
