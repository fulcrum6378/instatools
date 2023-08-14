package ir.mahdiparastesh.instatools.more

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.MutableLiveData
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.view.DataLoader
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import ir.mahdiparastesh.instatools.view.UiTools.vish

abstract class UserListActivity : BaseActivity(), DataLoader {
    private var countBadge: BadgeDrawable? = null

    abstract val bRefresher: SwipeRefreshLayout
    abstract val bTbShadow: View
    override val heightPixels: Int by lazy { dm.heightPixels }
    override var shouldShowJumper = MutableLiveData(false)
    override var anJumper: ObjectAnimator? = null

    companion object {
        const val HANDLE_LOADED = 0
    }

    override fun prepareListing(c: BaseActivity) {
        bRefresher.setOnRefreshListener { load() }
        super.prepareListing(c)
    }

    abstract fun load()

    override fun updateShadow() {
        if (bInitialised) bTbShadow.vish(rv()!!.computeVerticalScrollOffset() > 0)
    }

    override fun onRecyclerViewScrolled() {
        super.onRecyclerViewScrolled()
        updateShadow()
    }

    @SuppressLint("UnsafeOptInUsageError")
    protected fun updateCount(n: Int) {
        BadgeUtils.detachBadgeDrawable(countBadge, tbTitle!!)
        BadgeUtils.attachBadgeDrawable(
            BadgeDrawable.create(
                ContextThemeWrapper(this@UserListActivity, UiTools.materialTheme)
            ).apply {
                number = n
                backgroundColor = themeColor(android.R.attr.colorAccent)
                badgeTextColor =
                    if (night()) themeColor(android.R.attr.colorPrimary)
                    else color(R.color.defBG)
                countBadge = this
                maxCharacterCount = UiTools.MAX_BADGE_CHAR
            }, tbTitle!!
        )
    }
}
