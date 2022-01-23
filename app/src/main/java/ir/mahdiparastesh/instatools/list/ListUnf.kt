package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.ListUnfBinding

class ListUnf(val c: Main) : RecyclerView.Adapter<ListUnf.ViewHolder>() {
    class ViewHolder(val b: ListUnfBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListUnfBinding.inflate(c.layoutInflater, parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        h.b.name.text = c.m.unfollowers[i].name
    }

    override fun getItemCount() = c.m.unfollowers.size
}
