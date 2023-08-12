package ir.mahdiparastesh.instatools.more

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.View
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import ir.mahdiparastesh.instatools.view.UiTools.vish

abstract class UserListActivity : BaseActivity() {
    private var countBadge: BadgeDrawable? = null

    abstract val bRefresher: SwipeRefreshLayout
    abstract val bRv: RecyclerView
    abstract val bTbShadow: View
    abstract val bJumper: ImageView

    companion object {
        const val HANDLE_LOADED = 0
    }

    protected fun prepare() {
        bRefresher.setOnRefreshListener { load() }
        bRv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                bTbShadow.vish(bRv.computeVerticalScrollOffset() > 0)
                updateJumper()
            }
        })
        bJumper.setOnClickListener { bRv.smoothScrollToPosition(0) }
        bJumper.translationY = UiTools.jumperTrans(this)
        shouldShowJumper.observe(this) {
            anJumper?.cancel()
            anJumper = UiTools.anJumper(this, bJumper, it)
        }
    }

    abstract fun load()

    private var shouldShowJumper = MutableLiveData(false)
    private var anJumper: ObjectAnimator? = null
    private fun updateJumper() {
        (bRv.computeVerticalScrollOffset() > dm.heightPixels)
            .apply { if (this != shouldShowJumper.value) shouldShowJumper.value = this }
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
