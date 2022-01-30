package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListBoxBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools

class ListBox(val c: Main) : RecyclerView.Adapter<ListBox.ViewHolder>() {
    class ViewHolder(val b: ListBoxBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListBoxBinding
            .inflate(c.themeInflater(BaseActivity.Theme.TERTIARY), parent, false)
        b.name.typeface = c.fontRegular
        b.last.typeface = c.fontRegular
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.dmThreads == null) return
        val u = c.m.dmThreads!![i].users[0]
        Glide.with(c.c).load(u.profile_pic_url).into(h.b.photo)
        h.b.name.text = u.full_name
        h.b.last.text = c.getString(
            R.string.boxUntil, UiTools.date(c.m.dmThreads!![i].last_activity_at.toLong() / 1000L)
        )
        h.b.root.setOnClickListener {
        }
        UiTools.vis(h.b.sep, i < itemCount - 1)
    }

    override fun getItemCount() = c.m.dmThreads?.size ?: 0
}
