package ir.mahdiparastesh.instatools.list

import android.util.TypedValue
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.MassFollower
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListFwbBinding

class ListFwb(val c: MassFollower) : RecyclerView.Adapter<ListFwb.ViewHolder>() {
    private val tc = TypedValue().apply {
        c.theme.resolveAttribute(R.attr.colorAccent, this, true)
    }.data
    private val night = c.night()

    class ViewHolder(val b: ListFwbBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListFwbBinding.inflate(c.layoutInflater, parent, false)
        b.root.typeface = c.fontRegular
        if (night) b.root.setTextColor(tc)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val fwb = c.m.fwb.value?.getOrNull(i) ?: return
        h.b.root.text = fwb.user
        h.b.root.setOnClickListener {
        }
        h.b.root.setOnCloseIconClickListener {
        }
    }

    override fun getItemCount() = c.m.fwb.value?.size ?: 0
}
