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
import ir.mahdiparastesh.instatools.frag.PageUnf.Companion.MAX_UNFOLLOW_AD
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

class ListUnf(val c: Main, private val f: PageUnf) :
    RecyclerView.Adapter<AnyViewHolder<ListUnfBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListUnfBinding> {
        val b = ListUnfBinding.inflate(f.inflater, parent, false)
        b.name.typeface = c.fontRegular
        b.user.typeface = c.fontRegular
        b.name.textDirection =
            if (!c.dirRtl) TextView.TEXT_DIRECTION_LTR else TextView.TEXT_DIRECTION_RTL
        return AnyViewHolder(b)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(h: AnyViewHolder<ListUnfBinding>, i: Int) {
        val unf = c.m.unfollowers.value?.getOrNull(i) ?: return
        Glide.with(c.c).load(unf.pict).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${unf.name}"
        h.b.user.text = if (unf.unfollowedMeAt != null) c.getString(
            R.string.unfollowedAt, UiTools.date(unf.unfollowedMeAt!!)
        ) else unf.user
        h.b.root.alpha = if (unf.inFav) FAV_ALPHA else 1f

        h.b.root.setOnClickListener {
            val u = c.m.unfollowers.value?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(
                c.wrapTheme(BaseActivity.Theme.PRIMARY), c.fontRegular, it, R.menu.unf_more,
                Act().apply {
                    this[R.id.umViewInApp] = { Viewer.comeHere(c, u.user) }
                    this[R.id.umViewInInsta] = { UiTools.openProfile(c, u.user) }
                }, c.colorAc.value
            ).show()
        }
        h.b.unfollow.setOnClickListener {
            val u = c.m.unfollowers.value?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            if (!u.priv) unfollow(u)
            else AlertDialog.Builder(c).apply {
                setTitle(R.string.unfollow)
                setMessage(R.string.unfollowPV)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ -> unfollow(u) }
            }.show().stylise(c)
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.unfollowers.value?.size ?: 0

    private fun unfollow(unf: Friend) {
        Api<Rest>(
            c, Api.Type.UNFOLLOW.url.format(unf.id), Rest::class, PageUnf.handler,
            method = Request.Method.POST
        ) {
            if (it.status != "ok") {
                PageUnf.handler?.obtainMessage(PageUnf.HANDLE_COULD_NOT)?.sendToTarget()
                return@Api; }
            f.counter++
            if (f.counter >= MAX_UNFOLLOW_AD) {
                c.loadInterstitial(R.string.interUnfMany, true)
                f.counter = 0
            }
            Thread {
                if (unf.follows) c.dao.updateFriend(unf.apply { followed = false })
                else c.dao.deleteFriend(unf)
            }.start()
            val index = c.m.unfollowers.value!!.indexOf(unf)
            c.m.unfollowers.value!!.remove(unf)
            f.b.rv.adapter?.notifyItemRemoved(index)
            if (index > 0) f.b.rv.adapter?.notifyItemChanged(index - 1)
            f.b.rv.adapter?.notifyItemRangeChanged(index, c.m.unfollowers.value!!.size - 1)
            c.m.unfollowers.value = c.m.unfollowers.value
        }
    }

    companion object {
        const val FAV_ALPHA = 0.5f
    }
}
