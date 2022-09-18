package ir.mahdiparastesh.instatools.more

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.iterator
import androidx.media2.common.SessionPlayer
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ExpandableBinding
import ir.mahdiparastesh.instatools.databinding.GuestModeBinding
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish

abstract class BasePageMain : BasePage<Main>(), SwipeRefreshLayout.OnRefreshListener {
    val inflater: LayoutInflater by lazy { c.themeInflater(theme, c.layoutInflater) }

    abstract val theme: BaseActivity.Theme
    abstract val emptyIcon: Int
    private fun refresher(): SwipeRefreshLayout? = root?.findViewById(R.id.refresher)
    open fun expanded(): ExpandableBinding? = null

    protected open fun guestMode(parent: ConstraintLayout) {
        GuestModeBinding.inflate(c.themeInflater(theme, c.layoutInflater), parent, true)
        onLoaded(false, asGuest = true)
        for (ch in parent) if (ch is RecyclerView) ch.vis(false)
        refresher()?.isEnabled = false
        jumper()?.vis(false)
        rv()?.vis(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refresher()?.setOnRefreshListener(this)
        super.onViewCreated(view, savedInstanceState)
        if (Main.guest) {
            guestMode(view as ConstraintLayout); return; }

        empty()?.compoundDrawables?.getOrNull(1)?.colorFilter =
            PorterDuffColorFilter(c.wrapTheme(theme).themeColor(), PorterDuff.Mode.SRC_IN)
        error()?.setOnClickListener {
            refresher()?.isRefreshing = true
            onRefresh()
        }
    }

    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        refresher()?.isRefreshing = false
        super.onLoaded(isEmpty, asGuest)
    }

    override fun onFailed(message: String) {
        refresher()?.isRefreshing = false
        super.onFailed(message)
    }

    override fun onRecyclerViewScrolled() {
        super.onRecyclerViewScrolled()
        updateShadow()
    }

    override fun updateShadow() {
        if (bInitialised) c.b.tbShadow.vish(rv()!!.computeVerticalScrollOffset() > 0)
    }

    override fun onPause() {
        super.onPause()
        (expanded()?.slider?.adapter as ListCar?)?.players
            ?.forEach { if (it?.playerState == SessionPlayer.PLAYER_STATE_PLAYING) it.pause() }
    }
}
