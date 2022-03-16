package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListRliBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.json.Rest.Reel
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListRli(private val c: Viewer, private val f: PageRel, private val reel: () -> Reel?) :
    RecyclerView.Adapter<ListRli.ViewHolder>() {
    class ViewHolder(val b: ListRliBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListRliBinding.inflate(c.layoutInflater, parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val item = reel()?.items?.getOrNull(i) ?: return

        Glide.with(c.c)
            .load(item.thumb())
            .into(h.b.thumb)

        h.b.click.setOnClickListener {
            c.expandable.media =
                reel()?.items?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            c.expandable.thumb = h.b.root
            try {
                c.expandable.expand()
                f.jumper().vis(false)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) throw e
            }
        }
    }

    override fun getItemCount(): Int = reel()?.items?.size ?: 0
}
