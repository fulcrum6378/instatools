package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListRelBinding
import ir.mahdiparastesh.instatools.frag.PageRel

class ListRel(private val c: Viewer, private val f: PageRel) :
    RecyclerView.Adapter<ListRel.ViewHolder>() {
    class ViewHolder(val b: ListRelBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListRelBinding.inflate(c.layoutInflater, parent, false)
        b.root.layoutParams = b.root.layoutParams.apply {
            width = c.dm.widthPixels / 3
            height = c.dm.widthPixels / 2
        }
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val rel = c.m.vwReels?.getOrNull(i) ?: return

        Glide.with(c.c)
            .load(rel.thumb())
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            //.addListener(GlideShimmer(h.b.root, h.b.thumbnail))
            .into(h.b.thumbnail)
    }

    override fun getItemCount(): Int = c.m.vwReels?.size ?: 0
}
