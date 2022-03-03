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
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.ListQudBinding
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val qud = c.m.queueds?.getOrNull(i) ?: return

        // Main
        Glide.with(c.c).load(qud.thumb).into(h.b.thumb)
        h.b.user.text = "${i + 1}. ${qud.userName ?: "..."}"
        h.b.date.text = UiTools.date(qud.addedAt)

        // Status
        h.b.status.setAnimation(
            when {
                qud.failed -> R.raw.failed
                i > 0 || !Queuer.active.value!! -> R.raw.pending
                else -> R.raw.download
            }
        )
        val pad = if (!qud.failed)
            c.resources.getDimension(R.dimen.qudLoadingPad).toInt() else 0
        h.b.status.setPadding(pad, pad, pad, pad)
        h.b.status.isClickable = qud.failed

        // Clicks
        h.b.root.setOnClickListener {
            val q = c.m.queueds?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(c, it, R.menu.qud_more, Act().apply {
                this[R.id.qmRemove] = {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            c.dao.deleteQueued(q)
                        } catch (e: Exception) {
                        }
                        withContext(Dispatchers.Main) {
                            if (c.m.queueds == null) return@withContext
                            Queued.find(q, c.m.queueds!!)?.apply {
                                c.m.queueds!!.removeAt(this)
                                c.b.rv.adapter?.notifyItemRemoved(this)
                                c.b.rv.adapter?.notifyItemRangeChanged(
                                    this, c.m.queueds!!.size - 1
                                )
                            }
                        }
                    }
                }
                this[R.id.qmOpen] = {
                    c.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(q.link))
                        //.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }).show()
        }
        h.b.status.isClickable = qud.failed
        h.b.status.setOnClickListener(if (qud.failed) View.OnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                c.m.queueds?.getOrNull(h.layoutPosition)?.apply {
                    failed = false
                    c.dao.updateQueued(this)
                    withContext(Dispatchers.Main) {
                        c.b.rv.adapter?.notifyItemChanged(h.layoutPosition)
                        Downloads.initService(c)
                    }
                }
            }
        } else null)

        // Separator
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.queueds?.size ?: 0

    override fun onViewAttachedToWindow(h: ViewHolder) {
        h.b.status.resumeAnimation()
    }

    override fun onViewDetachedFromWindow(h: ViewHolder) {
        h.b.status.pauseAnimation()
    }
}
