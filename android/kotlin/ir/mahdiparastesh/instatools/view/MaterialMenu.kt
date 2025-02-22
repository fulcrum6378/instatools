package ir.mahdiparastesh.instatools.view

import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import androidx.annotation.StyleRes
import androidx.appcompat.widget.PopupMenu
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.util.BaseActivity

/** Helper class for making PopupMenus more quickly. */
class MaterialMenu(
    c: BaseActivity, v: View, res: Int, vararg actions: Pair<Int, (item: MenuItem) -> Unit>,
    @StyleRes theme: Int = R.style.Theme_InstaTools_Popup,
) : PopupMenu(ContextThemeWrapper(c, theme), v) {

    init {
        val map = hashMapOf<Int, (item: MenuItem) -> Unit>()
        for (act in actions) map[act.first] = act.second
        setOnMenuItemClickListener {
            if (it.itemId in map) {
                map[it.itemId]!!(it)
                true
            } else false
        }
        inflate(res)
    }
}
