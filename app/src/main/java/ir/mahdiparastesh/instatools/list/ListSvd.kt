package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.ListSvdBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class ListSvd(val c: Main) : RecyclerView.Adapter<ListSvd.ViewHolder>()  {
    class ViewHolder(val b: ListSvdBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListSvdBinding
            .inflate(c.themeInflater(BaseActivity.Theme.SECONDARY), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.saved == null) return
    }

    override fun getItemCount() = c.m.saved?.size ?: 0
}
