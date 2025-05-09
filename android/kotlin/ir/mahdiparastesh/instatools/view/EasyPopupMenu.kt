package ir.mahdiparastesh.instatools.view

import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.annotation.StyleRes
import ir.mahdiparastesh.instatools.base.BaseActivity

/** Helper class for creating [PopupMenu]s more easily */
class EasyPopupMenu(
    c: BaseActivity,
    v: View,
    res: Int,
    vararg actions: Pair<Int, (item: MenuItem) -> Unit>,
    @StyleRes theme: Int? = null,
) : PopupMenu(theme?.let { ContextThemeWrapper(c, it) } ?: c, v) {

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
