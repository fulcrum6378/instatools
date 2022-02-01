package ir.mahdiparastesh.instatools.list

import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.json.Dm

class ListThd(val c: Main, private val f: PageBox) : RecyclerView.Adapter<ListThd.ViewHolder>() {
    class ViewHolder(val b: ListThdBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListThdBinding.inflate(f.inflater, parent, false)
        onCreate(b, c.fontRegular)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.dmThread == null) return
        onBind(h.b, c.m.dmThread!!.items[i])
    }

    override fun getItemCount() = c.m.dmThread?.items?.size ?: 0

    companion object {
        fun onCreate(b: ListThdBinding, font: Typeface) {
            b.message.typeface = font
        }

        fun onBind(b: ListThdBinding, item: Dm) {

            // Layout
            b.area.layoutParams = (b.area.layoutParams as ConstraintLayout.LayoutParams).apply {
                horizontalBias = if (item.is_sent_by_viewer) 1f else 0f
            }
            b.message.layoutParams =
                (b.message.layoutParams as ConstraintLayout.LayoutParams).apply {
                    horizontalBias = if (item.is_sent_by_viewer) 1f else 0f
                }
            b.message.setBackgroundResource(
                if (item.is_sent_by_viewer) R.drawable.dm_from_me else R.drawable.dm_to_me
            )
            b.message.textAlignment =
                if (item.is_sent_by_viewer) TextView.TEXT_ALIGNMENT_VIEW_END
                else TextView.TEXT_ALIGNMENT_VIEW_START

            // Message
            b.message.text = item.text
        }
    }
}
