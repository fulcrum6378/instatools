package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListRliBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.api.Rest.Reel
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListRli(private val c: Viewer, private val f: PageRel, private val reel: () -> Reel?) :
    RecyclerView.Adapter<AnyViewHolder<ListRliBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListRliBinding> =
        AnyViewHolder(ListRliBinding.inflate(c.layoutInflater, parent, false))

    override fun onBindViewHolder(h: AnyViewHolder<ListRliBinding>, i: Int) {
        val item = reel()?.items?.getOrNull(i) ?: return
        h.b.number.text = "${i + 1}"

        Glide.with(c.c)
            .load(item.thumb())
            .into(h.b.thumb)

        h.b.click.setOnClickListener {
            c.expandable.media =
                reel()?.items?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            if (reel() is Rest.HighlightReel) {
                val rel = reel() as Rest.HighlightReel?
                c.expandable.media?.mahdi_reel_id = rel?.id
                c.expandable.media?.mahdi_reel_user_name = rel?.user?.username
            }
            c.expandable.thumb = h.b.root
            try {
                c.expandable.expand()
                f.jumper()?.vis(false)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) throw e
            }
        }
    }

    override fun getItemCount(): Int = reel()?.items?.size ?: 0
}
