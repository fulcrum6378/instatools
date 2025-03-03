package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListFavBinding
import ir.mahdiparastesh.instatools.frag.PageFav
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ListFav(val c: Main, private val f: PageFav) :
    RecyclerView.Adapter<AnyViewHolder<ListFavBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListFavBinding> {
        val b = ListFavBinding.inflate(f.layoutInflater, parent, false)
        b.name.textDirection =
            if (!c.dirRtl) TextView.TEXT_DIRECTION_LTR
            else TextView.TEXT_DIRECTION_RTL
        return AnyViewHolder(b)
    }

    override fun onBindViewHolder(h: AnyViewHolder<ListFavBinding>, i: Int) {
        val fav = c.c.fav?.getOrNull(i) ?: return
        Glide.with(c.c).load(fav.photo).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${fav.name}"
        h.b.user.text = fav.user
        h.b.root.setOnClickListener {
            val u = c.c.fav?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            Viewer.comeHere(c, u.id)
        }
        h.b.unFav.setOnClickListener {
            val f = c.c.fav?.getOrNull(h.layoutPosition)?.apply { tempDeleted = !tempDeleted }
                ?: return@setOnClickListener
            CoroutineScope(Dispatchers.IO).launch {
                if (f.tempDeleted) c.c.dao.deleteFavourite(f)
                else c.c.dao.addFavourite(f)
            }
            h.b.updateIcon(f.tempDeleted)
        }
        h.b.updateIcon(fav.tempDeleted)
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.c.fav?.size ?: 0

    private fun ListFavBinding.updateIcon(tempDeleted: Boolean) {
        unFav.setImageResource(if (tempDeleted) R.drawable.favourite_off else R.drawable.favourite_on)
    }
}
