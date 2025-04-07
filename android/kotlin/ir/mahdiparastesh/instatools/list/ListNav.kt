package ir.mahdiparastesh.instatools.list

import android.database.DataSetObserver
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.ListDrawerNavBinding

abstract class ListNav(protected val list: List<NavItem>) : ListAdapter {

    override fun areAllItemsEnabled(): Boolean = list.all { it.isEnabled }
    override fun getCount(): Int = list.size
    override fun getItem(position: Int): Any? = list[position]
    override fun getItemId(position: Int): Long = list[position].id.toLong()
    override fun getItemViewType(position: Int): Int = 0
    override fun getViewTypeCount(): Int = 1
    override fun hasStableIds(): Boolean = true
    override fun isEmpty(): Boolean = list.isEmpty()
    override fun isEnabled(position: Int): Boolean = list[position].isEnabled
}

class ListDrawerNav(private val c: Main, vararg list: NavItem) : ListNav(list.toList()) {

    override fun registerDataSetObserver(observer: DataSetObserver?) {}
    override fun unregisterDataSetObserver(observer: DataSetObserver?) {}

    override fun getView(i: Int, convertView: View?, parent: ViewGroup?): View? {
        val b = convertView?.let { ListDrawerNavBinding.bind(it) }
            ?: ListDrawerNavBinding.inflate(c.layoutInflater)

        b.icon.setImageResource(list[i].icon)
        b.text.setText(list[i].text)
        b.root.setOnClickListener(list[i].listener)

        return b.root
    }
}

class NavItem(
    @IdRes val id: Int,
    @DrawableRes val icon: Int,
    @StringRes val text: Int,
    var isEnabled: Boolean = true,
    val listener: View.OnClickListener
)
