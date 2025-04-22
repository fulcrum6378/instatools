package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.databinding.ListStoryBinding
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListStory(private val c: Viewer, var story: Story) :
    RecyclerView.Adapter<AnyViewHolder<ListStoryBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListStoryBinding> =
        AnyViewHolder(ListStoryBinding.inflate(c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListStoryBinding>, i: Int) {
        val med = story.items?.getOrNull(i) ?: return
        h.b.number.text = "${i + 1}"

        // load thumbnail
        Glide.with(c.c)
            .load(med.thumb())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .signature(ObjectKey(med.id()))
            .into(h.b.thumb)

        // is media already downloaded?
        h.b.stored.vis(
            if (c.c.downloadHistory.isEmpty())
                false
            else
                c.c.downloadHistory.anyStartsWith(
                    "${story.user.username}_" +
                        Utils.fileDateTime(Utils.compileSecondsTS(med.taken_at!!)) +
                        "_${med.id()}"
                )
        )

        // is media liked?
        h.b.liked.vis(med.has_liked == true)

        // clicks
        h.b.click.setOnClickListener {
            c.expandable.expand(
                story.carousel() ?: return@setOnClickListener,
                h.b.thumb,
                h.layoutPosition
            )
        }
    }

    override fun getItemCount(): Int = story.items?.size ?: 0
}
