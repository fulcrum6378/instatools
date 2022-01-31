package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.data.Unfollower
import ir.mahdiparastesh.instatools.databinding.ListUnfBinding
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.Act
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.MaterialMenu
import ir.mahdiparastesh.instatools.more.UiTools
import ir.mahdiparastesh.instatools.more.UiTools.Companion.vis

class ListUnf(val c: Main, private val f: PageUnf) : RecyclerView.Adapter<ListUnf.ViewHolder>() {
    // IndexOutOfBoundsException: Inconsistency detected. Invalid item position 373(offset:374)

    class ViewHolder(val b: ListUnfBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListUnfBinding
            .inflate(c.themeInflater(BaseActivity.Theme.PRIMARY), parent, false)
        b.name.typeface = c.fontRegular
        b.user.typeface = c.fontRegular
        return ViewHolder(b)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.unfollowers == null) return
        Glide.with(c.c).load(c.m.unfollowers!![i].photo).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${c.m.unfollowers!![i].name}"
        h.b.user.text = c.m.unfollowers!![i].user
        h.b.root.setOnClickListener {
            val user = c.m.unfollowers?.get(h.layoutPosition)?.user ?: return@setOnClickListener
            MaterialMenu(c, it, R.menu.unf_click, Act().apply {
                this[R.id.ucViewInApp] = {
                    c.startActivity(Intent(c, Viewer::class.java).apply {
                        putExtra(Viewer.EXTRA_USER, user)
                    })
                }
                this[R.id.ucViewInInsta] = {
                    UiTools.openProfile(c, user)
                }
            }).show()
        }
        h.b.unfollow.setOnClickListener {
            if (c.m.unfollowers == null) return@setOnClickListener
            if (!c.m.unfollowers!![h.layoutPosition].isPrivate)
                unfollow(c.m.unfollowers!![h.layoutPosition])
        }
        vis(h.b.sep, i < itemCount - 1)
    }

    override fun getItemCount() = c.m.unfollowers?.size ?: 0

    private fun unfollow(unf: Unfollower) {
        Api<Rest>(
            c, Api.Type.UNFOLLOW.url.format(unf.id.toString()), Rest::class,
            method = Request.Method.POST
        ) {
            c.pDao.deleteUnfollower(unf)
            val index = c.m.unfollowers!!.indexOf(unf)
            c.m.unfollowers!!.remove(unf)
            f.b.rv.adapter?.notifyItemRemoved(index)
            f.b.rv.adapter?.notifyItemRangeChanged(index, c.m.unfollowers!!.size - 1)
        }
    }
}
