package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.databinding.ListRliBinding
import ir.mahdiparastesh.instatools.frag.PageSto
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListRel(private val c: Viewer, private val f: PageSto, var story: Story) :
    RecyclerView.Adapter<AnyViewHolder<ListRliBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListRliBinding> =
        AnyViewHolder(ListRliBinding.inflate(c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListRliBinding>, i: Int) {
        val item = story.items?.getOrNull(i) ?: return
        h.b.number.text = "${i + 1}"

        Glide.with(c.c)
            .load(item.thumb())
            .into(h.b.thumb)

        h.b.click.setOnClickListener {
            c.expandable.media = story.items?.getOrNull(h.layoutPosition)
                ?: return@setOnClickListener
            c.expandable.thumb = h.b.root
            c.expandable.mediaOwner = c.mm.user // these Medias do not contains User information!
            try {
                c.expandable.expand()
                f.b.jumper.vis(false)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) throw e
            }
        }
    }

    override fun getItemCount(): Int = story.items?.size ?: 0
}
