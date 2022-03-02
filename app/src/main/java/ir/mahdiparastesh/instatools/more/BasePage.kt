package ir.mahdiparastesh.instatools.more

import android.animation.ObjectAnimator
import android.os.Handler
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.GuestModeBinding
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

abstract class BasePage(val c: Main) : Fragment(), BackStackOwner, Toolbar.OnMenuItemClickListener,
    SwipeRefreshLayout.OnRefreshListener {
    abstract var inflater: LayoutInflater
    abstract var handler: Handler?
    abstract val root: ConstraintLayout

    private fun refresher() = root.findViewById<SwipeRefreshLayout>(R.id.refresher)
    private fun rv() = root.findViewById<RecyclerView>(R.id.rv)
    private fun jumper() = root.findViewById<ImageView>(R.id.jumper)
    private fun empty() = root.findViewById<TextView>(R.id.empty)
    private fun error() = root.findViewById<ImageView>(R.id.error)
    private fun loading() = root.findViewById<LottieAnimationView>(R.id.loading)

    protected open fun guestMode(parent: ConstraintLayout, theme: BaseActivity.Theme) {
        val gb = GuestModeBinding.inflate(c.themeInflater(theme, c.layoutInflater), parent, true)
        gb.root.typeface = c.fontRegular
        onLoaded(false, asGuest = true)
        for (ch in parent) if (ch is RecyclerView) ch.vis(false)
        refresher().isEnabled = false
        jumper().vis(false)
        rv().vis(false)
    }

    protected fun essentials() {
        refresher().setOnRefreshListener(this)
        rv().addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
                updateJumper()
            }
        })
        jumper().apply {
            setOnClickListener { rv().smoothScrollToPosition(0) }
            translationY = UiTools.jumperTrans(c)
        }
        shouldShowJumper.observe(c) {
            anJumper?.cancel()
            anJumper = UiTools.anJumper(c, jumper(), it)
        }
        error().setOnClickListener {
            refresher().isRefreshing = true
            onRefresh()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = true

    open fun onLoaded(isEmpty: Boolean, asGuest: Boolean = false) {
        refresher().isRefreshing = false
        if (loading() != null && root.contains(loading())) {
            loading().animation?.cancel()
            root.removeView(loading())
        }
        error().vis(false)
        empty().vis(isEmpty)
        if (isEmpty) empty().typeface = c.fontRegular
    }

    open fun onFailed(message: String) {
        refresher().isRefreshing = false
        try {
            Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
        } catch (ignored: IllegalArgumentException) {
            // No suitable parent found from the given view. Please provide a valid view.
        }
        if (loading() != null && root.contains(loading())) {
            loading().animation?.cancel()
            root.removeView(loading())
        }
        if (rv().adapter == null) error().vis()
        empty().vis(false)
    }

    fun updateShadow() {
        c.b.tbShadow.vish(rv().computeVerticalScrollOffset() > 0)
    }

    var shouldShowJumper = MutableLiveData(false)
    private var anJumper: ObjectAnimator? = null
    open fun updateJumper() {
        (rv().computeVerticalScrollOffset() > c.dm.heightPixels)
            .apply { if (this != shouldShowJumper.value) shouldShowJumper.value = this }
    }

    companion object {
        const val HANDLE_FETCHED = 0
        const val HANDLE_ABORTED = 1
    }
}
