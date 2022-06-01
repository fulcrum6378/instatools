package ir.mahdiparastesh.instatools.list

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.gson.reflect.TypeToken
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListRelBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.json.Rest.HighlightReel
import ir.mahdiparastesh.instatools.json.Rest.StoryReel
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListRel(private val c: Viewer, private val f: PageRel) :
    RecyclerView.Adapter<AnyViewHolder<ListRelBinding>>() {
    private val begHigh: Int by lazy { if (c.m.vwReels?.any { it is StoryReel } == true) 0 else 1 }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListRelBinding> {
        val b = ListRelBinding.inflate(c.layoutInflater, parent, false)
        b.title.typeface = c.fontBold
        b.desc.typeface = c.fontLight
        return AnyViewHolder(b)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(h: AnyViewHolder<ListRelBinding>, i: Int) {
        val rel = c.m.vwReels?.getOrNull(i) ?: return

        h.b.title.text =
            if (rel is StoryReel) c.getString(R.string.vwStoryReel)
            else "${i + begHigh}. ${(rel as HighlightReel).title}"
        h.b.desc.text = c.getString(
            R.string.vwReelDesc,
            if (rel is StoryReel) rel.items!!.size else (rel as HighlightReel).media_count.toInt()
        )
        if (rel is StoryReel) h.b.icon.setImageResource(R.drawable.instagram)
        else (rel as HighlightReel).cover_media?.cropped_image_version.apply {
            if (this != null) Glide.with(c.c)
                .load(url)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(h.b.icon)
            else h.b.icon.setImageDrawable(null)
        }

        h.b.reel.scaleY = if (rel.opened) 1f else 0f
        h.b.reel.layoutParams = h.b.reel.layoutParams.apply {
            height = if (rel.opened) c.resources.getDimension(R.dimen.vwReelHeight).toInt() else 0
        }
        h.b.reel.vis(rel.opened)
        if (rel.opened && rel is HighlightReel)
            loadHlItems(i) { h.b.reel.adapter?.notifyDataSetChanged() }
        h.b.header.setOnClickListener {
            c.m.vwReels?.getOrNull(h.layoutPosition)?.apply {
                anSlide?.cancel()
                opened = !opened
                if (opened && this is HighlightReel)
                    loadHlItems(h.layoutPosition) { h.b.reel.adapter?.notifyDataSetChanged() }
                anSlide =
                    ObjectAnimator.ofFloat(h.b.reel, View.SCALE_Y, if (opened) 1f else 0f).apply {
                        addUpdateListener {
                            h.b.reel.layoutParams = h.b.reel.layoutParams.apply {
                                height = (c.resources.getDimension(R.dimen.vwReelHeight)
                                        * it.animatedValue as Float).toInt()
                            }
                        }
                        addListener(
                            onStart = {
                                h.b.reel.vis(true)
                                if (opened) h.b.shadow.vis()
                            }, onEnd = {
                                h.b.reel.vis(opened)
                                if (!opened) h.b.shadow.vis(false)
                            }
                        )
                        start()
                    }
            }
        }
        if (h.b.reel.adapter == null)
            h.b.reel.adapter = ListRli(c, f) { c.m.vwReels?.getOrNull(i) }
        else h.b.reel.adapter?.notifyDataSetChanged()

        h.b.line.vis(i < itemCount - 1)
    }

    override fun getItemCount(): Int = c.m.vwReels?.size ?: 0

    private fun loadHlItems(i: Int, onEnd: () -> Unit) {
        (c.m.vwReels?.getOrNull(i) as HighlightReel?)?.apply {
            if (items != null) return@loadHlItems
            Api<Rest.Reels<HighlightReel>>(
                c, Api.Endpoint.REEL_ITEM.url.format(id), Rest.Reels::class, PageRel.handler,
                cache = true, typeToken = object : TypeToken<Rest.Reels<HighlightReel>>() {}.type,
            ) { reels ->
                items = reels.reels.getOrDefault(id, null)?.items
                onEnd()
            }
        }
    }
}
