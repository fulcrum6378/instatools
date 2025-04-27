package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListFavBinding
import ir.mahdiparastesh.instatools.frag.PageFav
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListFav(val c: Main, private val f: PageFav) :
    RecyclerView.Adapter<AnyViewHolder<ListFavBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListFavBinding> {
        val b = ListFavBinding.inflate(f.inflater, parent, false)
        b.name.textDirection =
            if (!c.dirRtl) TextView.TEXT_DIRECTION_LTR
            else TextView.TEXT_DIRECTION_RTL
        return AnyViewHolder(b)
    }

    override fun onBindViewHolder(h: AnyViewHolder<ListFavBinding>, i: Int) {
        val fav = c.vm.favourites.getOrNull(i) ?: return
        Glide.with(c.c)
            .load(fav.photo)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(h.b.photo)
        h.b.name.text = "${i + 1}. ${fav.name}"
        h.b.user.text = fav.user
        h.b.root.setOnClickListener {
            Viewer.comeHere(c, fav.id)
        }
        h.b.unFav.setOnClickListener {
            fav.tempDeleted = !fav.tempDeleted
            if (fav.tempDeleted) {
                c.c.removeFavourite(fav)
                //c.vm.favCount.value = c.vm.favCount.value!! - 1
            } else {
                c.c.addFavourite(fav)
                //c.vm.favCount.value = c.vm.favCount.value!! + 1
            }
            h.b.updateIcon(fav.tempDeleted)
        }
        h.b.updateIcon(fav.tempDeleted)
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.vm.favourites.size

    private fun ListFavBinding.updateIcon(tempDeleted: Boolean) {
        unFav.setImageResource(if (tempDeleted) R.drawable.favourite_off else R.drawable.favourite_on)
    }
}
