package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.databinding.ListRelBinding
import ir.mahdiparastesh.instatools.view.AnyViewHolder

class ListRel(private val c: Viewer, var story: Story) :
    RecyclerView.Adapter<AnyViewHolder<ListRelBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListRelBinding> =
        AnyViewHolder(ListRelBinding.inflate(c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListRelBinding>, i: Int) {
        val med = story.items?.getOrNull(i) ?: return
        h.b.number.text = "${i + 1}"

        Glide.with(c.c)
            .load(med.thumb())
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .signature(ObjectKey(med.id()))
            .into(h.b.thumb)

        h.b.click.setOnClickListener {
            c.expandable.expand(
                story.items?.getOrNull(h.layoutPosition) ?: return@setOnClickListener,
                h.b.root,
                c.mm.user?.username, // these Media instances do not contains User information!
                c.mm.user?.id()
            )
        }
    }

    override fun getItemCount(): Int = story.items?.size ?: 0
}
