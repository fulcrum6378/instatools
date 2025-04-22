package ir.mahdiparastesh.instatools.view

import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes

/** Data model for a navigation button used in simple menus. */
data class NavItem(
    @IdRes val id: Int,
    @DrawableRes val icon: Int,
    @StringRes val text: Int,
    val listener: View.OnClickListener
)
