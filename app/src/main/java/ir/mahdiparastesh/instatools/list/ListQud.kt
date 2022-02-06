package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListQudBinding
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListQud(val c: Downloads) : RecyclerView.Adapter<ListQud.ViewHolder>() {
    class ViewHolder(val b: ListQudBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListQudBinding.inflate(c.layoutInflater, parent, false)
        b.user.typeface = c.fontRegular
        b.date.typeface = c.fontRegular
        return ViewHolder(b)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.queueds == null) return

        // Main
        Glide.with(c.c).load(c.m.queueds!![i].thumb).into(h.b.thumb)
        h.b.user.text = "${i + 1}. ${c.m.queueds!![i].userName ?: "..."}"
        h.b.date.text = UiTools.date(c.m.queueds!![i].addedAt)

        // Status
        h.b.status.setAnimation(if (!c.m.queueds!![i].failed) R.raw.download else R.raw.failed)
        val pad = if (!c.m.queueds!![i].failed)
            c.resources.getDimension(R.dimen.qudLoadingPad).toInt() else 0
        h.b.status.setPadding(pad, pad, pad, pad)
        h.b.status.isClickable = c.m.queueds!![i].failed
        h.b.status.setOnClickListener(if (c.m.queueds!![i].failed) View.OnClickListener {
            c.m.queueds!![h.layoutPosition].failed = false
            c.dao.updateQueued(c.m.queueds!![h.layoutPosition])
            c.b.rv.adapter?.notifyItemChanged(h.layoutPosition)
            Downloads.initService(c)
        } else null)

        // Clicks
        h.b.root.setOnClickListener {
            MaterialMenu(c, it, R.menu.qud_more, Act().apply {
                this[R.id.qmRemove] = {
                    if (c.m.queueds != null && c.m.queueds!!.size > h.layoutPosition) {
                        c.dao.deleteQueued(c.m.queueds!![h.layoutPosition])
                        c.m.queueds!!.removeAt(h.layoutPosition)
                        c.b.rv.adapter?.notifyItemRemoved(h.layoutPosition)
                        c.b.rv.adapter?.notifyItemRangeChanged(
                            h.layoutPosition,
                            c.m.queueds!!.size - 1
                        )
                    }
                }
                this[R.id.qmOpen] = {
                    if (c.m.queueds != null) c.startActivity(
                        Intent(
                            Intent.ACTION_VIEW, Uri.parse(c.m.queueds!![h.layoutPosition].link)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }).show()
        }

        // Separator
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.queueds?.size ?: 0
}
