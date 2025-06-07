package ir.mahdiparastesh.instatools.list

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.databinding.ListStoryBinding
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListStory(
    private val c: InstaTools,
    private val inflater: LayoutInflater,
    private val expandable: Expandable,
    var story: Story
) : RecyclerView.Adapter<AnyViewHolder<ListStoryBinding>>() {

    private val typeVideo: Drawable by lazy { expandable.c.drawable(R.drawable.video) }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListStoryBinding> =
        AnyViewHolder(ListStoryBinding.inflate(inflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListStoryBinding>, i: Int) {
        val med = story.items?.getOrNull(i) ?: return
        h.b.number.text = "${i + 1}"

        // load thumbnail
        Glide.with(c)
            .load(med.thumb())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .signature(ObjectKey(med.id()))
            .into(h.b.thumb)

        // media type
        h.b.type.setImageDrawable(
            if (med.video_versions != null) typeVideo else null
        )

        // is media already downloaded?
        h.b.stored.vis(
            if (c.downloadHistory.isEmpty())
                false
            else c.downloadHistory.anyStartsWith(
                "${story.user.username}_${Utils.fileDateTime(med.taken_at!!)}_${med.id()}"
            )
        )

        // is media liked?
        h.b.liked.vis(med.has_liked == true)

        // clicks
        h.b.click.setOnClickListener {
            expandable.expand(
                story.carousel() ?: return@setOnClickListener,
                h.b.thumb,
                h.layoutPosition
            )
        }
    }

    override fun getItemCount(): Int = story.items?.size ?: 0
}
