package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.ListSvdBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class ListSvd(val c: Main) : RecyclerView.Adapter<ListSvd.ViewHolder>() {
    class ViewHolder(val b: ListSvdBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListSvdBinding
            .inflate(c.themeInflater(BaseActivity.Theme.SECONDARY), parent, false)
        b.root.layoutParams = b.root.layoutParams.apply {
            width = c.dm.widthPixels / 3
            height = c.dm.widthPixels / 3
        }
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.saved == null) return
        Glide.with(c.c).load(c.m.saved!![i].display_url).into(h.b.thumbnail)
        h.b.root.setOnClickListener { }
    }

    override fun getItemCount() = c.m.saved?.size ?: 0
}
