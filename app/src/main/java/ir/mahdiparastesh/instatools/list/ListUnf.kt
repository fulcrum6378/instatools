package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.ListUnfBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools
import ir.mahdiparastesh.instatools.more.UiTools.Companion.vis

class ListUnf(val c: Main) : RecyclerView.Adapter<ListUnf.ViewHolder>() {
    class ViewHolder(val b: ListUnfBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListUnfBinding
            .inflate(c.themeInflater(BaseActivity.Theme.PRIMARY), parent, false)
        return ViewHolder(b)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.unfollowers == null) return
        Glide.with(c.c).load(c.m.unfollowers!![i].photo).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${c.m.unfollowers!![i].name}"
        h.b.user.text = c.m.unfollowers!![i].user
        h.b.root.setOnClickListener {
            if (c.m.unfollowers == null) return@setOnClickListener
            UiTools.openProfile(c, c.m.unfollowers!![h.layoutPosition].user)
        }
        vis(h.b.sep, i < itemCount - 1)
    }

    override fun getItemCount() = c.m.unfollowers?.size ?: 0
}
