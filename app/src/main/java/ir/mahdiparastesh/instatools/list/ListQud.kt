package ir.mahdiparastesh.instatools.list

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListQudBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools

class ListQud(val c: Downloads) : RecyclerView.Adapter<ListQud.ViewHolder>() {
    class ViewHolder(val b: ListQudBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListQudBinding
            .inflate(c.themeInflater(BaseActivity.Theme.SECONDARY), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.queueds == null) return
        Glide.with(c.c).load(c.m.queueds!![i].thumb).into(h.b.thumb)
        h.b.user.text = c.m.queueds!![i].userName
        h.b.status.setAnimation(if (!c.m.queueds!![i].failed) R.raw.loading else R.raw.failed)
        h.b.status.isClickable = c.m.queueds!![i].failed
        h.b.status.setOnClickListener(if (c.m.queueds!![i].failed) View.OnClickListener {
            c.m.queueds!![h.layoutPosition].failed = false
            c.pDao.updateQueued(c.m.queueds!![h.layoutPosition])
            c.b.rv.adapter?.notifyItemChanged(h.layoutPosition)
            c.initService()
        } else null)
        // TODO: MAKE THEM CANCELLABLE
        UiTools.vis(h.b.sep, i < itemCount - 1)
    }

    override fun getItemCount() = c.m.queueds?.size ?: 0
}
