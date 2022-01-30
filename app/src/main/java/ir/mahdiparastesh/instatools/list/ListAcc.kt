package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.ListAccBinding
import ir.mahdiparastesh.instatools.more.UiTools.Companion.vis

class ListAcc(val c: Login) : RecyclerView.Adapter<ListAcc.ViewHolder>() {
    class ViewHolder(val b: ListAccBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListAccBinding.inflate(c.layoutInflater, parent, false)
        b.name.typeface = c.fontRegular
        b.user.typeface = c.fontRegular
        return ViewHolder(b)
    }

    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        val guest = c.m.accounts[i].id < 0L
        Glide.with(c.c).load(if (!guest) c.m.accounts[i].photo else R.mipmap.launcher)
            .into(h.b.photo)
        if (!guest) h.b.name.text = c.m.accounts[i].name
        else h.b.name.setText(R.string.guest)
        vis(h.b.name, guest || c.m.accounts[i].name != "")
        vis(h.b.user, !guest && c.m.accounts[i].user != "")
        h.b.user.text = c.m.accounts[i].user
        h.b.root.setOnClickListener {
            c.selectAccount(c.m.accounts[h.layoutPosition])
        }
        vis(h.b.sep, i < itemCount - 1)
    }

    override fun getItemCount() = c.m.accounts.size
}
