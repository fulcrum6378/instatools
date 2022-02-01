package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListBoxBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.view.UiTools

class ListBox(val c: Main, private val f: PageBox) : RecyclerView.Adapter<ListBox.ViewHolder>() {
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
        h.b.name.text = if (u.full_name != "") u.full_name else u.username
        h.b.last.text = c.getString(
            R.string.boxUntil, UiTools.date(c.m.dmThreads!![i].last_activity_at.toLong() / 1000L)
        )
        h.b.root.setOnClickListener {
            c.m.dmThread = c.m.dmThreads?.get(h.layoutPosition)
            f.adapt()
            f.thdThread = f.FetchSomeDm().also { it.start() }
        }
        UiTools.vis(h.b.sep, i < itemCount - 1)
    }

    override fun getItemCount() = c.m.dmThreads?.size ?: 0
}
