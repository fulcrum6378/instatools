package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListSvdBinding
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.GlideShimmer

class ListSvd(val c: Main, private val f: PageSvd) : RecyclerView.Adapter<ListSvd.ViewHolder>() {
    val expandable = Expandable(
        c, f.b.expanded, f.b.expandedIndicator, f.b.root, f.handler,
        c.color(if (!c.night) R.color.defBG else R.color.CSD)
    )

    inner class ViewHolder(val b: ListSvdBinding) : RecyclerView.ViewHolder(b.root) {
        fun getItemDetails(): ItemDetailsLookup.ItemDetails<String?> =
            object : ItemDetailsLookup.ItemDetails<String?>() {
                override fun getPosition(): Int = layoutPosition
                override fun getSelectionKey(): String? = c.m.saved?.get(position)?.id
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListSvdBinding.inflate(f.inflater, parent, false)
        b.root.layoutParams = b.root.layoutParams.apply {
            width = c.dm.widthPixels / 3
            height = c.dm.widthPixels / 3
        }
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.saved == null) return

        Glide.with(c.c)
            .load(c.m.saved!![i].thumbnail_src)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .addListener(GlideShimmer(h.b.root, h.b.thumbnail))
            .into(h.b.thumbnail)

        h.b.click.setBackgroundResource(
            if (f.tracker == null || !f.tracker!!.isSelected(c.m.saved!![i].id)) R.drawable.button
            else R.drawable.selected
        )
        h.b.click.setOnClickListener {
            expandable.thumb = it
            try {
                expandable.expand(c.m.saved!![h.layoutPosition].shortcode)
            } catch (ignored: NullPointerException) {
            }
        }
    }

    override fun getItemCount() = c.m.saved?.size ?: 0
}
