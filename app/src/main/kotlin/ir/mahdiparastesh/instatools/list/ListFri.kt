package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Friends
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.databinding.ListFriBinding
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListFri(val c: Friends) : RecyclerView.Adapter<AnyViewHolder<ListFriBinding>>() {
    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListFriBinding> {
        val b = ListFriBinding.inflate(c.layoutInflater, parent, false)
        b.name.textDirection =
            if (!c.dirRtl) AppCompatTextView.TEXT_DIRECTION_LTR
            else AppCompatTextView.TEXT_DIRECTION_RTL
        return AnyViewHolder(b)
    }

    override fun onBindViewHolder(h: AnyViewHolder<ListFriBinding>, i: Int) {
        val fri = c.mm.friends.getOrNull(i) ?: return
        Glide.with(c.c).load(fri.pict).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${fri.name}"
        h.b.user.text = fri.user
        h.b.root.setOnClickListener {
            val u = c.mm.friends.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            Viewer.comeHere(c, u.user)
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.mm.friends.size
}