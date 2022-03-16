package ir.mahdiparastesh.instatools.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.contains
import androidx.core.view.iterator
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.GuestModeBinding
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

abstract class BasePageMain : BasePage<Main>(), SwipeRefreshLayout.OnRefreshListener {
    abstract var inflater: LayoutInflater
    private lateinit var guestAdBanner: AdView

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

        guestAdBanner = UiTools.adaptiveBanner(c, "ca-app-pub-9457309151954418/5535427358")
        root.addView(guestAdBanner, UiTools.adaptiveBannerLp())
        guestAdBanner.loadAd(AdRequest.Builder().build())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refresher().setOnRefreshListener(this)
        super.onViewCreated(view, savedInstanceState)

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
        updateShadow()
    }

    override fun updateShadow() {
        if (bInitialised) c.b.tbShadow.vish(rv().computeVerticalScrollOffset() > 0)
    }
}
