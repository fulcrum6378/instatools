package ir.mahdiparastesh.instatools.list

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.ListRelBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.api.Rest.HighlightReel
import ir.mahdiparastesh.instatools.api.Rest.StoryReel
import ir.mahdiparastesh.instatools.api.Versioned
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.getOrNull
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.xFromSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ListRel(private val c: Viewer, private val f: PageRel) :
    RecyclerView.Adapter<AnyViewHolder<ListRelBinding>>() {
    private val begHigh: Int by lazy { if (c.mm.vwReels?.any { it is StoryReel } == true) 0 else 1 }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListRelBinding> =
        AnyViewHolder(ListRelBinding.inflate(c.layoutInflater, parent, false))

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(h: AnyViewHolder<ListRelBinding>, i: Int) {
        val rel = c.mm.vwReels?.getOrNull(i) ?: return

        // Reel data
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
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(h.b.icon)
            else h.b.icon.setImageDrawable(null)
        }

        // Actions
        h.b.downloadAll.setOnClickListener {
            if (rel is StoryReel || rel.items != null) downloadAll(rel)
            else loadHlItems(h.layoutPosition) { downloadAll(rel) }
        }

        // ListRli manager
        h.b.reel.scaleY = if (rel.opened) 1f else 0f
        h.b.reel.layoutParams = h.b.reel.layoutParams.apply {
            height = if (rel.opened) c.resources.getDimension(R.dimen.vwReelHeight).toInt() else 0
        }
        h.b.reel.vis(rel.opened)
        if (rel.opened && rel is HighlightReel)
            loadHlItems(i) { h.b.reel.adapter?.notifyDataSetChanged() }
        h.b.header.setOnClickListener {
            c.mm.vwReels?.getOrNull(h.layoutPosition)?.apply {
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
            h.b.reel.adapter = ListRli(c, f) { c.mm.vwReels?.getOrNull(i) }
        else h.b.reel.adapter?.notifyDataSetChanged()

        h.b.line.vis(i < itemCount - 1)
    }

    override fun getItemCount(): Int = c.mm.vwReels?.size ?: 0

    private fun loadHlItems(i: Int, onEnd: () -> Unit) {
        (c.mm.vwReels?.getOrNull(i) as HighlightReel?)?.apply {
            if (items != null) return@loadHlItems
            CoroutineScope(Dispatchers.IO).launch {
                val reels = Api.call<Rest.Reels<HighlightReel>>(
                    Api.Endpoint.REEL_ITEM.url.format(id),
                    Rest.Reels::class, arrayOf(HighlightReel::class),
                    cache = true, onError = { code ->
                        UiTools.snackbar(f.b.root, Api.error(code), Snackbar.LENGTH_LONG)
                    }
                )
                if (reels == null) return@launch
                items = reels.reels.getOrNull(id)?.items
                withContext(Dispatchers.Main) { onEnd() }
            }
        }
    }

    private fun downloadAll(rel: Rest.Reel) {
        CoroutineScope(Dispatchers.IO).launch {
            for (rli in rel.items!!) c.dao.addQueued(
                Queued(
                    Persistent.now(), rli.link() ?: "",
                    if (rli.taken_at > 0.0) rli.taken_at.xFromSeconds() else Persistent.now(),
                    rli.user.pk,
                    rel.user.username,
                    rli.pk ?: rli.id,
                    rli.nearest(Versioned.BEST),
                    rli.thumb(),
                    rli.media_type.toInt().toByte(),
                    rli.video_duration?.toLong(),
                    rli.caption?.text
                )
            )
            withContext(Dispatchers.Main) { Downloads.initService(c, "") }
        }
    }
}
