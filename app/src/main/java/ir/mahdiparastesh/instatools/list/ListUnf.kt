package ir.mahdiparastesh.instatools.list

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.bumptech.glide.Glide
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.databinding.ListUnfBinding
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListUnf(val c: Main, private val f: PageUnf) : RecyclerView.Adapter<ListUnf.ViewHolder>() {
    class ViewHolder(val b: ListUnfBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ListUnfBinding.inflate(f.inflater, parent, false)
        b.name.typeface = c.fontRegular
        b.user.typeface = c.fontRegular
        b.name.textDirection =
            if (!c.dirRtl) TextView.TEXT_DIRECTION_LTR else TextView.TEXT_DIRECTION_RTL
        return ViewHolder(b)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(h: ViewHolder, i: Int) {
        if (c.m.unfollowers.value == null) return
        Glide.with(c.c).load(c.m.unfollowers.value!![i].photo).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${c.m.unfollowers.value!![i].name}"
        h.b.user.text = c.m.unfollowers.value!![i].user
        h.b.root.setOnClickListener {
            val u = c.m.unfollowers.value?.get(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(c, it, R.menu.unf_more, Act().apply {
                this[R.id.umViewInApp] = { Viewer.comeHere(c, u.id, u.user) }
                this[R.id.umViewInInsta] = { UiTools.openProfile(c, u.user) }
            }).show()
        }
        h.b.unfollow.setOnClickListener {
            if (c.m.unfollowers.value == null) return@setOnClickListener
            if (!c.m.unfollowers.value!![h.layoutPosition].private)
                unfollow(c.m.unfollowers.value!![h.layoutPosition])
            else AlertDialog.Builder(c).apply {
                setTitle(R.string.unfollow)
                setMessage(R.string.unfollowPV)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    unfollow(c.m.unfollowers.value!![h.layoutPosition])
                }
            }.show().stylise(c)
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.unfollowers.value?.size ?: 0

    private fun unfollow(unf: Friend) {
        Api<Rest>(
            c, Api.Type.UNFOLLOW.url.format(unf.id), Rest::class, f.handler,
            method = Request.Method.POST
        ) {
            if (it.status != "ok") {
                f.handler?.obtainMessage(PageUnf.HANDLE_COULD_NOT)?.sendToTarget()
                return@Api; }
            Thread {
                if (unf.follows) c.dao.updateFriend(unf.apply { followed = false })
                else c.dao.deleteFriend(unf)
            }.start()
            val index = c.m.unfollowers.value!!.indexOf(unf)
            c.m.unfollowers.value!!.remove(unf)
            f.b.rv.adapter?.notifyItemRemoved(index)
            if (index > 0) f.b.rv.adapter?.notifyItemChanged(index - 1)
            f.b.rv.adapter?.notifyItemRangeChanged(index, c.m.unfollowers.value!!.size - 1)
        }
    }
}
