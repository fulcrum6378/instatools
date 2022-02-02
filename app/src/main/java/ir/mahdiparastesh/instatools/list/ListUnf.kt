package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListUnf(val c: Main, private val f: PageUnf) : RecyclerView.Adapter<ListUnf.ViewHolder>() {
    class ViewHolder(val b: ListUnfBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListUnfBinding.inflate(f.inflater, parent, false)
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
            MaterialMenu(c, it, R.menu.unf_more, Act().apply {
                this[R.id.umViewInApp] = {
                    c.startActivity(Intent(c, Viewer::class.java).apply {
                        putExtra(Viewer.EXTRA_USER, user)
                    })
                }
                this[R.id.umViewInInsta] = {
                    UiTools.openProfile(c, user)
                }
            }).show()
        }
        h.b.unfollow.setOnClickListener {
            if (c.m.unfollowers == null) return@setOnClickListener
            if (!c.m.unfollowers!![h.layoutPosition].isPrivate)
                unfollow(c.m.unfollowers!![h.layoutPosition])
            else AlertDialog.Builder(c).apply {
                setTitle(R.string.unfollow)
                setMessage(R.string.unfollowPV)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    unfollow(c.m.unfollowers!![h.layoutPosition])
                }
            }.create().show()
        }
        vis(h.b.sep, i < itemCount - 1)
    }

    override fun getItemCount() = c.m.unfollowers?.size ?: 0

    private fun unfollow(unf: Unfollower) {
        Api<Rest>(
            c, Api.Type.UNFOLLOW.url.format(unf.id.toString()), Rest::class,
            PageUnf.theHandler, method = Request.Method.POST
        ) {
            if (it.status != "ok") {
                PageUnf.theHandler?.obtainMessage(PageUnf.Action.COULD_NOT.ordinal)?.sendToTarget()
                return@Api; }
            c.pDao.deleteUnfollower(unf)
            val index = c.m.unfollowers!!.indexOf(unf)
            c.m.unfollowers!!.remove(unf)
            f.b.rv.adapter?.notifyItemRemoved(index)
            f.b.rv.adapter?.notifyItemRangeChanged(index, c.m.unfollowers!!.size - 1)
        }
    }
}
