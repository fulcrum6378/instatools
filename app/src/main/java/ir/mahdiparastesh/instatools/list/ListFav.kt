package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Favourites
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListFavBinding
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListFav(val c: Favourites) : RecyclerView.Adapter<ListFav.ViewHolder>() {
    class ViewHolder(val b: ListFavBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListFavBinding.inflate(c.layoutInflater, parent, false)
        b.name.typeface = c.fontRegular
        b.user.typeface = c.fontRegular
        b.name.textDirection =
            if (!c.dirRtl) TextView.TEXT_DIRECTION_LTR else TextView.TEXT_DIRECTION_RTL
        return ViewHolder(b)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.fav == null) return
        Glide.with(c.c).load(c.m.fav!![i].photo).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${c.m.fav!![i].name}"
        h.b.user.text = c.m.fav!![i].user
        h.b.root.setOnClickListener {
            val u = c.m.fav?.get(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(c, it, R.menu.fav_more, Act().apply {
                this[R.id.umViewInApp] = { Viewer.comeHere(c, u.id, u.user) }
                this[R.id.umViewInInsta] = { UiTools.openProfile(c, u.user) }
            }).show()
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.fav?.size ?: 0
}
