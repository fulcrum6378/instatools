package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Favourites
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListFavBinding
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListFav(val c: Favourites) : RecyclerView.Adapter<AnyViewHolder<ListFavBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListFavBinding> {
        val b = ListFavBinding.inflate(c.layoutInflater, parent, false)
        b.name.textDirection =
            if (!c.dirRtl) AppCompatTextView.TEXT_DIRECTION_LTR
            else AppCompatTextView.TEXT_DIRECTION_RTL
        return AnyViewHolder(b)
    }

    override fun onBindViewHolder(h: AnyViewHolder<ListFavBinding>, i: Int) {
        val fav = c.m.fav?.getOrNull(i) ?: return
        Glide.with(c.c).load(fav.photo).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${fav.name}"
        h.b.user.text = fav.user
        h.b.root.setOnClickListener {
            val u = c.m.fav?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            Viewer.comeHere(c, u.id)
        }
        h.b.unFav.setOnClickListener {
            val f = c.m.fav?.getOrNull(h.layoutPosition)?.apply { tempDeleted = !tempDeleted }
                ?: return@setOnClickListener
            CoroutineScope(Dispatchers.IO).launch {
                if (f.tempDeleted) c.dao.deleteFavourite(f)
                else c.dao.addFavourite(f)

                val listSize = c.dao.countFavourites()
                withContext(Dispatchers.Main) {
                    if (f.tempDeleted) c.updateCount(c, listSize)
                    else c.updateCount(c, listSize)
                }
            }
            h.b.updateIcon(f.tempDeleted)
        }
        h.b.updateIcon(fav.tempDeleted)
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.fav?.size ?: 0

    private fun ListFavBinding.updateIcon(tempDeleted: Boolean) {
        unFav.setImageResource(if (tempDeleted) R.drawable.non_favourite else R.drawable.favourite)
    }
}
