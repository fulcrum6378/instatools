package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.text.util.Linkify
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.view.UiTools.Companion.z
import java.util.*

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

        @SuppressLint("SetTextI18n")
        fun onBind(b: ListThdBinding, dm: Dm) {

            // Layout
            b.area.layoutParams = (b.area.layoutParams as ConstraintLayout.LayoutParams).apply {
                horizontalBias = if (dm.is_sent_by_viewer) 1f else 0f
            }
            b.message.layoutParams =
                (b.message.layoutParams as ConstraintLayout.LayoutParams).apply {
                    horizontalBias = if (dm.is_sent_by_viewer) 1f else 0f
                }
            b.time.layoutParams =
                (b.time.layoutParams as ConstraintLayout.LayoutParams).apply {
                    if (dm.is_sent_by_viewer) {
                        startToEnd = ConstraintLayout.LayoutParams.UNSET
                        endToStart = b.message.id
                    } else {
                        startToEnd = b.message.id
                        endToStart = ConstraintLayout.LayoutParams.UNSET
                    }
                }
            b.message.setBackgroundResource(
                if (dm.is_sent_by_viewer) R.drawable.dm_from_me else R.drawable.dm_to_me
            )
            b.message.textAlignment =
                if (dm.is_sent_by_viewer) TextView.TEXT_ALIGNMENT_VIEW_END
                else TextView.TEXT_ALIGNMENT_VIEW_START

            // Message
            when {
                dm.text != null -> b.message.text = dm.text
                dm.action_log != null -> b.message.text = dm.action_log.description
                dm.link != null -> {
                    b.message.text = dm.link.link_context.link_url
                    Linkify.addLinks(b.message, Linkify.ALL)
                }
                dm.profile != null -> {
                    b.message.text = "@${dm.profile.username}"
                }
            }
            val cal = Calendar.getInstance().apply { timeInMillis = dm.timestamp.toLong() }
            b.time.text =
                "${z(cal[Calendar.HOUR_OF_DAY])}:${z(cal[Calendar.MINUTE])}:${z(cal[Calendar.SECOND])}"
        }
    }
}
