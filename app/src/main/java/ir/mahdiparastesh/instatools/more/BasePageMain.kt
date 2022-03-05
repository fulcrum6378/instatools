package ir.mahdiparastesh.instatools.more

import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.core.view.iterator
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.GuestModeBinding
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

abstract class BasePageMain(c: Main) : BasePage<Main>(c), SwipeRefreshLayout.OnRefreshListener {
    abstract var inflater: LayoutInflater

    private fun refresher(): SwipeRefreshLayout = root.findViewById(R.id.refresher)
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

    override fun essentials() {
        refresher().setOnRefreshListener(this)
        super.essentials()
        error().setOnClickListener {
            refresher().isRefreshing = true
            onRefresh()
        }
    }

    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        refresher().isRefreshing = false
        super.onLoaded(isEmpty, asGuest)
        if (loading() != null && root.contains(loading())) {
            loading().animation?.cancel()
            root.removeView(loading())
        }
        error().vis(false)
        empty().vis(isEmpty)
        if (isEmpty) empty().typeface = c.fontRegular
    }

    override fun onFailed(message: String) {
        refresher().isRefreshing = false
        super.onFailed(message)
        if (loading() != null && root.contains(loading())) {
            loading().animation?.cancel()
            root.removeView(loading())
        }
        if (rv().adapter == null) error().vis()
        empty().vis(false)
    }

    override fun onRecyclerViewScrolled() {
        super.onRecyclerViewScrolled()
        updateJumper()
    }

    override fun updateShadow() {
        c.b.tbShadow.vish(rv().computeVerticalScrollOffset() > 0)
    }
}
