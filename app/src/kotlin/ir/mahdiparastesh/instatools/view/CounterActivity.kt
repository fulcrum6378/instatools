package ir.mahdiparastesh.instatools.view

import android.annotation.SuppressLint
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.view.UiTools.themeColor

abstract class CounterActivity : BaseActivity() {
    private var countBadge: BadgeDrawable? = null
    var numCache: Int? = null

    @SuppressLint("UnsafeOptInUsageError")
    fun updateCount(n: Int) {
        BadgeUtils.detachBadgeDrawable(countBadge, tbTitle!!)
        BadgeUtils.attachBadgeDrawable(
            BadgeDrawable.create(
                ContextThemeWrapper(this@CounterActivity, UiTools.materialTheme)
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
        numCache = n
    }
}
