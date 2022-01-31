package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class ListThd(val c: Main) : RecyclerView.Adapter<ListThd.ViewHolder>() {
    class ViewHolder(val b: ListThdBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListThdBinding
            .inflate(c.themeInflater(BaseActivity.Theme.TERTIARY), parent, false)
        b.message.typeface = c.fontRegular
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.dmThread == null) return
        val item = c.m.dmThread!!.items[i]

        // Layout
        h.b.area.layoutParams = (h.b.area.layoutParams as ConstraintLayout.LayoutParams).apply {
            horizontalBias = if (item.is_sent_by_viewer) 1f else 0f
        }
        h.b.message.layoutParams =
            (h.b.message.layoutParams as ConstraintLayout.LayoutParams).apply {
                horizontalBias = if (item.is_sent_by_viewer) 1f else 0f
            }
        h.b.message.setBackgroundResource(
            if (item.is_sent_by_viewer) R.drawable.dm_from_me else R.drawable.dm_to_me
        )
        h.b.message.textAlignment =
            if (item.is_sent_by_viewer) TextView.TEXT_ALIGNMENT_VIEW_END
            else TextView.TEXT_ALIGNMENT_VIEW_START

        // Message
        h.b.message.text = item.text
    }

    override fun getItemCount() = c.m.dmThread?.items?.size ?: 0
}
