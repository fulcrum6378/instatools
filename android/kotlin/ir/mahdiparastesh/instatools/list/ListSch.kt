package ir.mahdiparastesh.instatools.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.base.BaseActivity
import ir.mahdiparastesh.instatools.databinding.ListAccBinding
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListSch(val c: Main) : RecyclerView.Adapter<AnyViewHolder<ListAccBinding>>() {
    private val inflater = c.themeInflater(BaseActivity.Theme.PRIMARY)
    private val hPad = c.resources.getDimension(R.dimen.mainPadH).toInt()

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListAccBinding> {
        val b = ListAccBinding.inflate(inflater, parent, false)
        b.root.setPadding(hPad, 0, hPad, 0)
        b.root.removeView(b.more)
        return AnyViewHolder(b)
    }

    override fun onBindViewHolder(h: AnyViewHolder<ListAccBinding>, i: Int) {
        val u = c.vm.schRes?.getOrNull(i)?.user ?: return

        Glide.with(c.c)
            .load(u.profile_pic_url)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(h.b.photo)
        h.b.name.text = u.full_name
        h.b.name.vis(u.full_name != "")
        h.b.user.vis(u.username != "")
        h.b.user.text = u.username
        h.b.root.setOnClickListener {
            Viewer.comeHere(c, u.id(), u.username!!)
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.vm.schRes?.size ?: 0
}
