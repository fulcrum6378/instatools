package ir.mahdiparastesh.instatools.view

import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import androidx.annotation.StyleRes
import androidx.appcompat.widget.PopupMenu
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.more.BaseActivity

/** Represents a PopupMenu action. */
typealias Act = HashMap<Int, (item: MenuItem) -> Unit>

/** Helper class for making PopupMenus more quickly. */
class MaterialMenu(
    c: BaseActivity, v: View, res: Int, actions: Act,
    @StyleRes theme: Int = R.style.Theme_InstaTools_Popup
) : PopupMenu(ContextThemeWrapper(c, theme), v) {

    init {
        setOnMenuItemClickListener {
            if (it.itemId in actions) {
                actions[it.itemId]!!(it)
                true
            } else false
        }
        inflate(res)
    }
}
