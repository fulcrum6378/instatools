package ir.mahdiparastesh.instatools.view

import android.text.SpannableString
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.forEach
import ir.mahdiparastesh.instatools.more.BaseActivity

typealias Act = HashMap<Int, (item: MenuItem) -> Unit>

class MaterialMenu(val c: BaseActivity, v: View, res: Int, actions: Act) :
    PopupMenu(ContextThemeWrapper(c, c.theme), v) {
    init {
        setOnMenuItemClickListener {
            if (it.itemId in actions) {
                actions[it.itemId]!!(it)
                true
            } else false
        }
        inflate(res)

    }

    override fun show() {
        menu.forEach {
            val mNewTitle = SpannableString(it.title)
            mNewTitle.setSpan(
                CustomTypefaceSpan("", c.fontRegular, c.dm.density * 16f), 0,
                mNewTitle.length, SpannableString.SPAN_INCLUSIVE_INCLUSIVE
            )
            it.title = mNewTitle
        }
        super.show()
    }
}
