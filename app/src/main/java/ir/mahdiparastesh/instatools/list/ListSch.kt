package ir.mahdiparastesh.instatools.list

import android.content.Intent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListAccBinding
import ir.mahdiparastesh.instatools.more.UiTools

class ListSch(val c: Main) : RecyclerView.Adapter<ListSch.ViewHolder>() {
    private val hPad = c.resources.getDimension(R.dimen.mainPadH).toInt()

    class ViewHolder(val b: ListAccBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListAccBinding.inflate(c.layoutInflater, parent, false)
        b.name.typeface = c.fontRegular
        b.user.typeface = c.fontRegular
        b.root.setPadding(hPad, 0, hPad, 0)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.schRes == null || c.schRes!!.size <= i) return
        Glide.with(c.c).load(c.schRes!![i].user.profile_pic_url).into(h.b.photo)
        h.b.name.text = c.schRes!![i].user.full_name
        UiTools.vis(h.b.name, c.schRes!![i].user.full_name != "")
        UiTools.vis(h.b.user, c.schRes!![i].user.username != "")
        h.b.user.text = c.schRes!![i].user.username
        h.b.root.setOnClickListener {
            c.startActivity(Intent(c, Viewer::class.java).apply {
                putExtra(Viewer.EXTRA_USER, c.schRes!![h.layoutPosition].user.username)
            })
        }
        UiTools.vis(h.b.sep, i < itemCount - 1)
    }

    override fun getItemCount() = c.schRes?.size ?: 0
}
