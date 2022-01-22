package ir.mahdiparastesh.instatools.more

import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.forEach
import androidx.core.view.get
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.internal.BaselineLayout

class UiTools {
    companion object {
        fun bnvTitles(bnv: BottomNavigationView): List<AppCompatTextView> {
            val list = ArrayList<AppCompatTextView>()
            (bnv[0] as BottomNavigationMenuView).forEach {
                val item = it as BottomNavigationItemView
                // 0 => FrameLayout (icon) and 1 => BaselineLayout (title)
                val title = item[1] as BaselineLayout // has 2 AppCompatTextView
                list.add(title[1] as AppCompatTextView) // Take the second one
            }
            return list.toList()
        }
    }
}
