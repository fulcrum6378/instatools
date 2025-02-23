package ir.mahdiparastesh.instatools.list

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.data.Friend.Companion.specialSort
import ir.mahdiparastesh.instatools.databinding.ListUnfBinding
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.MaterialMenu
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val unf = c.mm.unfollowers.value?.getOrNull(i) ?: return

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
        val alpha =
            if (unf.unfollowed || unf.inFav) 0.5f
            else 1f
        arrayOf(h.b.photo, h.b.name, h.b.user, h.b.unfollow).forEach { it.alpha = alpha }

        Glide.with(c.c).load(unf.pict).into(h.b.photo)
        h.b.name.text = "${i + 1}. ${unf.name}"
        h.b.user.text =
            if (unf.unfollowedMeAt != null && !unf.unfollowed)
                c.getString(R.string.unfollowedAt, Utils.date(unf.unfollowedMeAt!!))
            else unf.user

        h.b.root.setOnClickListener {
            val u = c.mm.unfollowers.value?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            MaterialMenu(
                c, it, R.menu.unf_more,
                R.id.umViewInApp to { Viewer.comeHere(c, u.user) },
                R.id.umViewInInsta to { UiTools.openProfile(c, u.user) },
                R.id.umToFav to { toggleFav(u) },
                theme = R.style.Theme_InstaTools_Popup_Primary
            ).apply {
                if (!u.unfollowed) menu.findItem(R.id.umToFav)
                    .setTitle(if (u.inFav) R.string.removeFav else R.string.addToFav)
                else menu.removeItem(R.id.umToFav)
            }.show()
        }
        h.b.unfollow.setOnClickListener {
            val u = c.mm.unfollowers.value?.getOrNull(h.layoutPosition) ?: return@setOnClickListener
            if (!u.priv) unfollow(u)
            else MaterialAlertDialogBuilder(
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

    override fun getItemCount() = c.mm.unfollowers.value?.size ?: 0

    private fun unfollow(unf: Friend) {
        CoroutineScope(Dispatchers.IO).launch {
            if ((try {
                    Api.json<GraphQl>(
                        Api.Endpoint.QUERY.url, true, GraphQlQuery.UNFOLLOW.body(unf.id)
                    ).data!!.xdt_destroy_friendship!!.friendship_status!!.following
                } catch (e: Api.FailureException) {
                    withContext(Dispatchers.Main) {
                        UiTools.snackbar(
                            f.b.root, c.getString(UiTools.apiError(e.code), e.code), c.b.bnv,
                            Snackbar.LENGTH_SHORT
                        )
                    }
                    return@launch
                }) != false
            ) {
                UiTools.snackbar(f.b.root, R.string.unfCouldNot, c.b.bnv, Snackbar.LENGTH_SHORT)
                return@launch; }

            c.incrementCounter(Settings.spUnfollowCount)
            if (unf.follows) c.dao.updateFriend(unf.apply { followed = false })
            else c.dao.deleteFriend(unf)

            withContext(Dispatchers.Main) {
                c.mm.unfollowers.value?.indexOf(unf)?.also { index ->
                    c.mm.unfollowers.value?.getOrNull(index)?.unfollowed = true
                    f.b.rv.adapter?.notifyItemChanged(index)
                }
                c.mm.unfollowers.value = c.mm.unfollowers.value
                if (c.mm.unfollowers.value.isNullOrEmpty()) f.onLoaded()
            }
        }
    }

    private fun toggleFav(u: Friend) {
        CoroutineScope(Dispatchers.IO).launch {
            val fav = u.toFavourite()
            if (!u.inFav) c.dao.addFavourite(fav)
            else c.dao.deleteFavouriteById(u.id)

            withContext(Dispatchers.Main) {
                var id: String? = null
                var favNow = false
                if (!u.inFav) {
                    c.m.fav?.add(fav)
                    id = fav.id
                    favNow = true
                } else {
                    c.m.fav?.removeAll { f -> f.id == u.id }
                    id = u.id
                }
                Friend.find(id, c.mm.unfollowers.value)?.also { before ->
                    c.mm.unfollowers.value?.getOrNull(before)?.inFav = favNow
                    c.mm.unfollowers.value?.specialSort()
                    Friend.find(id, c.mm.unfollowers.value)?.also { after ->
                        f.b.rv.adapter?.notifyItemMoved(before, after)
                        when {
                            before > after -> f.b.rv.adapter
                                ?.notifyItemRangeChanged(after, (before - after) + 1)
                            after > before -> f.b.rv.adapter
                                ?.notifyItemRangeChanged(before, (after - before) + 1)
                            else -> f.b.rv.adapter?.notifyItemChanged(after)
                        }
                    }
                }
            }
        }
    }
}
