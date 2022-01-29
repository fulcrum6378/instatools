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
import ir.mahdiparastesh.instatools.more.Act
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.MaterialMenu
import ir.mahdiparastesh.instatools.more.UiTools
import java.util.*

class ListQud(val c: Downloads) : RecyclerView.Adapter<ListQud.ViewHolder>() {
    class ViewHolder(val b: ListQudBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListQudBinding
            .inflate(c.themeInflater(BaseActivity.Theme.SECONDARY), parent, false)
        return ViewHolder(b)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.queueds == null) return

        // Main
        Glide.with(c.c).load(c.m.queueds!![i].thumb).into(h.b.thumb)
        h.b.user.text = "${i + 1}. ${c.m.queueds!![i].userName ?: "..."}"
        val cal = Calendar.getInstance().apply { timeInMillis = c.m.queueds!![i].addedAt }
        h.b.date.text = "${cal[Calendar.YEAR]}.${UiTools.z(cal[Calendar.MONTH] + 1)}." +
                "${UiTools.z(cal[Calendar.DAY_OF_MONTH])} - ${UiTools.z(cal[Calendar.HOUR_OF_DAY])}:" +
                "${UiTools.z(cal[Calendar.MINUTE])}:${UiTools.z(cal[Calendar.SECOND])}"

        // Status
        h.b.status.setAnimation(if (!c.m.queueds!![i].failed) R.raw.download else R.raw.failed)
        val pad = if (!c.m.queueds!![i].failed)
            c.resources.getDimension(R.dimen.qudLoadingPad).toInt() else 0
        h.b.status.setPadding(pad, pad, pad, pad)
        h.b.status.isClickable = c.m.queueds!![i].failed
        h.b.status.setOnClickListener(if (c.m.queueds!![i].failed) View.OnClickListener {
            c.m.queueds!![h.layoutPosition].failed = false
            c.pDao.updateQueued(c.m.queueds!![h.layoutPosition])
            c.b.rv.adapter?.notifyItemChanged(h.layoutPosition)
            Downloads.initService(c)
        } else null)

        // Clicks
        h.b.root.setOnClickListener {
            MaterialMenu(c, it, R.menu.qud_click, Act().apply {
                this[R.id.qcRemove] = {
                    if (c.m.queueds != null) {
                        c.pDao.deleteQueued(c.m.queueds!![h.layoutPosition])
                        c.m.queueds!!.removeAt(h.layoutPosition)
                        c.b.rv.adapter?.notifyItemRemoved(h.layoutPosition)
                        c.b.rv.adapter?.notifyItemRangeChanged(
                            h.layoutPosition,
                            c.m.queueds!!.size - 1
                        )
                    }
                }
                this[R.id.qcOpen] = {
                    if (c.m.queueds != null) c.startActivity(
                        Intent(
                            Intent.ACTION_VIEW, Uri.parse(c.m.queueds!![h.layoutPosition].link)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }).show()
        }

        // Separator
        UiTools.vis(h.b.sep, i < itemCount - 1)
    }

    override fun getItemCount() = c.m.queueds?.size ?: 0
}
