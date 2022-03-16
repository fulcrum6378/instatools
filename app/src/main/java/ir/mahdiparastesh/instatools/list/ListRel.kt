package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListRelBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.json.Rest.HighlightReel
import ir.mahdiparastesh.instatools.json.Rest.StoryReel
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListRel(private val c: Viewer, private val f: PageRel) :
    RecyclerView.Adapter<ListRel.ViewHolder>() {
    class ViewHolder(val b: ListRelBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListRelBinding.inflate(c.layoutInflater, parent, false)
        b.title.typeface = c.fontBold
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val rel = c.m.vwReels?.getOrNull(i) ?: return

        h.b.title.text = if (rel is StoryReel) c.getString(R.string.vwStoryReel, rel.items.size)
        else {
            val hl = rel as HighlightReel
            c.getString(R.string.vwHighlightReel, i, hl.title, hl.media_count.toInt())
        }
        if (rel is StoryReel) h.b.icon.setImageResource(R.drawable.instagram)
        else (rel as HighlightReel).cover_media?.cropped_image_version.apply {
            if (this != null) Glide.with(c.c)
                .load(url)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(h.b.icon)
            else h.b.icon.setImageDrawable(null)
        }

        h.b.header.setOnClickListener { }

        h.b.line.vis(i < itemCount - 1)
    }

    override fun getItemCount(): Int = c.m.vwReels?.size ?: 0

    override fun onViewDetachedFromWindow(h: ViewHolder) {
        super.onViewDetachedFromWindow(h)
        Glide.with(c.c).clear(h.b.icon)
    }
}
