package ir.mahdiparastesh.instatools.view

import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import ir.mahdiparastesh.instatools.R

typealias Act = HashMap<Int, (item: MenuItem) -> Unit>

class MaterialMenu(val c: ContextThemeWrapper, v: View, res: Int, actions: Act) :
    PopupMenu(ContextThemeWrapper(c, R.style.Theme_InstaTools_Popup), v) {

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
