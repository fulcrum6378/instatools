package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListPrfBinding
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.GlideShimmer

class ListVwr(val c: Viewer) : RecyclerView.Adapter<ListVwr.ViewHolder>() {
    val expandable = Expandable(
        c, c.b.expanded, c.b.expandedIndicator, c.b.refresher, Viewer.handler,
        c.color(if (!c.night()) R.color.defBG else R.color.CSD)
    )

    inner class ViewHolder(val b: ListPrfBinding) : RecyclerView.ViewHolder(b.root) {
        fun getItemDetails(): ItemDetailsLookup.ItemDetails<String?> =
            object : ItemDetailsLookup.ItemDetails<String?>() {
                override fun getPosition(): Int = layoutPosition
                override fun getSelectionKey(): String? =
                    c.m.vwUser?.edges()?.get(position)?.node?.id
            }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListPrfBinding.inflate(c.layoutInflater, parent, false)
        b.root.layoutParams = b.root.layoutParams.apply {
            width = c.dm.widthPixels / 3
            height = c.dm.widthPixels / 3
        }
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.vwUser == null) return

        Glide.with(c.c)
            .load(c.m.vwUser!!.edges()!![i].node.thumbnail_src)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .addListener(GlideShimmer(h.b.root, h.b.thumbnail))
            .into(h.b.thumbnail)

        h.b.click.setBackgroundResource(
            if (c.tracker == null || !c.tracker!!.isSelected(c.m.vwUser!!.edges()!![i].node.id)) R.drawable.button
            else R.drawable.selected
        )
        h.b.click.setOnClickListener {
            expandable.thumb = it
            try {
                expandable.expand(c.m.vwUser!!.edges()!![h.layoutPosition].node.shortcode)
            } catch (ignored: NullPointerException) {
            }
        }
    }

    override fun getItemCount() = c.m.vwUser?.edges()?.size ?: 0
}
