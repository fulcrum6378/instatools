package ir.mahdiparastesh.instatools.view

import android.text.SpannableString
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.forEach
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.more.BaseActivity

typealias Act = HashMap<Int, (item: MenuItem) -> Unit>

class MaterialMenu(
    val c: BaseActivity, v: View, res: Int, actions: Act, private val ca: Int? = null
) : PopupMenu(ContextThemeWrapper(c, c.theme), v) {
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
        menu.forEach { it.stylise(c, ca) }
        super.show()
    }

    companion object {
        fun MenuItem.stylise(c: BaseActivity, ca: Int? = null) {
            if (title == null) return
            title = SpannableString(title).apply {
                setSpan(
                    CustomTypefaceSpan(
                        "", c.fontRegular, c.resources.getDimension(R.dimen.menuFont),
                        ca ?: TypedValue().apply {
                            c.theme.resolveAttribute(R.attr.colorAccent, this, true)
                        }.data
                    ), 0, length, SpannableString.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
        }
    }
}
