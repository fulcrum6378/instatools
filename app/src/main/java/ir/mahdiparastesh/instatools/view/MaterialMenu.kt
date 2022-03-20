package ir.mahdiparastesh.instatools.view

import android.graphics.Typeface
import android.text.SpannableString
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.forEach
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.more.BaseActivity

typealias Act = HashMap<Int, (item: MenuItem) -> Unit>

class MaterialMenu(
    val c: ContextThemeWrapper, private val font: Typeface, v: View, res: Int, actions: Act,
    private val ca: Int? = null
) : PopupMenu(androidx.appcompat.view.ContextThemeWrapper(c, c.theme), v) {
    constructor(c: BaseActivity, v: View, res: Int, actions: Act, ca: Int? = null) :
            this(c, c.fontRegular, v, res, actions, ca)

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
        menu.forEach { it.stylise(c, font, ca) }
        super.show()
    }

    companion object {
        fun MenuItem.stylise(
            c: ContextThemeWrapper, font: Typeface, ca: Int? = null, size: Int = R.dimen.menuFont
        ) {
            if (title == null) return
            title = SpannableString(title).apply {
                setSpan(
                    CustomTypefaceSpan(
                        font, c.resources.getDimension(size),
                        if (ca == -1) null else ca ?: TypedValue().apply {
                            c.theme.resolveAttribute(R.attr.colorAccent, this, true)
                        }.data
                    ), 0, length, SpannableString.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
        }

        fun MenuItem.stylise(c: BaseActivity, ca: Int? = null, size: Int = R.dimen.menuFont) {
            stylise(c, c.fontRegular, ca, size)
        }
    }
}
