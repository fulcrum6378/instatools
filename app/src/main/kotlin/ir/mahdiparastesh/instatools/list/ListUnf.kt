package ir.mahdiparastesh.instatools.list

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
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
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.view.Act
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis

class ListUnf(val c: Main, private val f: PageUnf) :
    RecyclerView.Adapter<AnyViewHolder<ListUnfBinding>>() {

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): AnyViewHolder<ListUnfBinding> {
        val b = ListUnfBinding.inflate(f.inflater, parent, false)
        b.name.textDirection =
            if (!c.dirRtl) AppCompatTextView.TEXT_DIRECTION_LTR
            else AppCompatTextView.TEXT_DIRECTION_RTL
        return AnyViewHolder(b)
    }

    override fun onBindViewHolder(h: AnyViewHolder<ListUnfBinding>, i: Int) {
        val unf = c.m.unfollowers.value?.getOrNull(i) ?: return

        h.b.photo.vis(!unf.unfollowed)
        h.b.name.vis(!unf.unfollowed)
        h.b.unfollow.vis(!unf.unfollowed)
        h.b.user.layoutParams = (h.b.user.layoutParams as ConstraintLayout.LayoutParams)
            .apply { width = if (unf.unfollowed) ViewGroup.LayoutParams.MATCH_PARENT else 0 }
        h.b.user.textAlignment =
            if (unf.unfollowed) AppCompatTextView.TEXT_ALIGNMENT_CENTER
            else AppCompatTextView.TEXT_ALIGNMENT_VIEW_START
        // Presumably after invoking "notifyItemMoved()" the alpha value of the root is animated;
        // so if you change it statically here, your changes won't survive the animation.
        val alpha = when {
            unf.unfollowed -> OFF_ALPHA
            unf.inFav -> OFF_ALPHA
            else -> 1f
        }
        arrayOf(h.b.photo, h.b.name, h.b.user, h.b.unfollow).forEach { it.alpha = alpha }

        Glide.with(c.c).load(unf.pict).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${unf.name}"
        h.b.user.text = if (unf.unfollowedMeAt != null) c.getString(
            R.string.unfollowedAt, UiTools.date(unf.unfollowedMeAt!!)
        ) else unf.user

        h.b.root.setOnClickListener {
            val u = c.m.unfollowers.value?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(
                c, it, R.menu.unf_more, Act().apply {
                    this[R.id.umViewInApp] = { Viewer.comeHere(c, u.user) }
                    this[R.id.umViewInInsta] = { UiTools.openProfile(c, u.user) }
                    this[R.id.umToFav] = { toggleFav(u) }
                }, R.style.Theme_InstaTools_Popup_Primary
            ).apply {
                if (!u.unfollowed) menu.findItem(R.id.umToFav)
                    .setTitle(if (u.inFav) R.string.removeFav else R.string.addToFav)
                else menu.removeItem(R.id.umToFav)
            }.show()
        }
        h.b.unfollow.setOnClickListener {
            val u = c.m.unfollowers.value?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            if (!u.priv) unfollow(u)
            else AlertDialog.Builder(
                ContextThemeWrapper(c, R.style.Theme_InstaTools_Dialog_Primary)
            ).apply {
                setTitle(R.string.unfollow)
                setMessage(R.string.unfollowPV)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ -> unfollow(u) }
            }.show()
        }
        h.b.sep.vis(i < itemCount - 1)
    }

    override fun getItemCount() = c.m.unfollowers.value?.size ?: 0

    private fun unfollow(unf: Friend) {
        f.reqQueue.adder = Api<Rest>(
            c, Api.Endpoint.UNFOLLOW.url.format(unf.id), Rest::class, null,
            method = Request.Method.POST, autoQueue = false, onError = { res ->
                if (res?.statusCode == 429) {
                    var showing429 = true
                    c.loadInterstitial(R.string.interUnfMany) { !showing429 }
                    AlertDialog.Builder(
                        ContextThemeWrapper(c, R.style.Theme_InstaTools_Dialog_Primary)
                    ).apply {
                        setTitle(R.string.unfollow)
                        setMessage(R.string.unfollowedSoMany)
                        setNeutralButton(R.string.ok, null)
                        setOnDismissListener {
                            showing429 = false
                            c.showInterstitial()
                        }
                    }.show()
                } else PageUnf.handler?.obtainMessage(PageUnf.HANDLE_COULD_NOT)?.sendToTarget()
            }
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
            c.m.unfollowers.value?.indexOf(unf)?.also { index ->
                c.m.unfollowers.value?.getOrNull(index)?.unfollowed = true
                f.b.rv.adapter?.notifyItemChanged(index)
            }
            c.m.unfollowers.value = c.m.unfollowers.value
            if (c.m.unfollowers.value.isNullOrEmpty()) f.emptied(true)
        }
    }

    private fun toggleFav(u: Friend) {
        Thread {
            val fav = u.toFavourite()
            if (!u.inFav) c.dao.addFavourite(fav)
            else c.dao.deleteFavouriteById(u.id)
            PageUnf.handler?.obtainMessage(
                PageUnf.HANDLE_FAV_CHANGED, if (!u.inFav) fav else u.id
            )?.sendToTarget()
        }.start()
    }

    companion object {
        const val OFF_ALPHA = 0.5f
    }
}
