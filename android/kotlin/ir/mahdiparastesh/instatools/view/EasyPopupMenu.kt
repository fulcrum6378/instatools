package ir.mahdiparastesh.instatools.view

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.style.TypefaceSpan
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.annotation.StyleRes
import androidx.core.view.forEach
import ir.mahdiparastesh.instatools.R
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
        menu.applyFont(c)
    }

    companion object {

        /** Forces theming on a fucking old API. */
        fun Menu.applyFont(c: Context) {
            val font = c.resources.getFont(R.font.medium)
            forEach { item ->
                val s = SpannableString(item.title)
                s.setSpan(
                    object : TypefaceSpan(font) {
                        override fun updateDrawState(ds: TextPaint) {
                            ds.setTypeface(font)
                        }

                        override fun updateMeasureState(paint: TextPaint) {
                            paint.setTypeface(font)
                        }
                    },
                    0, s.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
                item.title = s
            }
        }
    }
}
