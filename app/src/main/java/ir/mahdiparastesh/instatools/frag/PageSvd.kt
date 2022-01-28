package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.GridLayoutManager
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.PageSvdBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.more.BackStackOwner
import ir.mahdiparastesh.instatools.more.BaseActivity

class PageSvd(val c: Main) : Fragment(), BackStackOwner {
    lateinit var b: PageSvdBinding
    lateinit var tracker: SelectionTracker<String>

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageSvdBinding.inflate(
            c.themeInflater(BaseActivity.Theme.SECONDARY, inf), parent, false
        )
        if (!Main.guest) b.refresher.setOnRefreshListener {
            c.m.nextSaved = null
            c.m.saved = null
            fetchSome()
        }
        when {
            Main.guest -> {
                // TODO: GUEST MODE
            }
            c.m.saved != null -> adapt()
            else -> fetchSome()
        }
        return b.root
    }

    private fun fetchSome() {
        if (c.m.nextSaved?.has_next_page == false) {
            b.refresher.isRefreshing = false
            return
        }
        if (c.m.saved == null) Api<Profile>(
            c, Api.Type.SAVED_FIRST.url.format(c.m.acc!!.user), Profile::class
        ) { profile ->
            val media = profile.graphql?.user?.edge_saved_media ?: return@Api
            c.m.nextSaved = media.page_info
            c.m.saved = ArrayList(media.edges.map { it.node })
            adapt()
            fetchSome()
        } else Api<Profile.GraphQlResponse>(
            c, Api.Type.SAVED.url.format(
                c.m.acc!!.id, c.m.saved!!.size, c.m.nextSaved?.end_cursor ?: ""
            ), Profile.GraphQlResponse::class
        ) { res ->
            val media = res.data.user?.edge_saved_media ?: return@Api
            c.m.nextSaved = media.page_info
            c.m.saved?.addAll(media.edges.map { it.node })
            adapt()
            fetchSome()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter == null) {
            b.rv.layoutManager = GridLayoutManager(c, 3)
            b.rv.adapter = ListSvd(c, this)
            tracker = SelectionTracker.Builder(
                "saved", b.rv,
                MyItemKeyProvider(), MyDetailsLookup(),
                StorageStrategy.createStringStorage()
            )/*.withSelectionPredicate(
                SelectionPredicates.createSelectAnything()
            )*/.build()
            tracker.addObserver(SelectObserver())
        } else b.rv.adapter?.notifyDataSetChanged()
    }

    override fun goBack(): Boolean {
        (b.rv.adapter as ListSvd?)?.let {
            if (it.zoomed) {
                it.collapse(); return@goBack true; }
        }
        return false
    }

    inner class MyItemKeyProvider : ItemKeyProvider<String>(SCOPE_CACHED) {
        override fun getKey(i: Int): String = c.m.saved!![i].id
        override fun getPosition(key: String): Int {
            for (i in c.m.saved!!.indices) if (c.m.saved!![i].id == key) return i
            return -1
        }
    }

    inner class MyDetailsLookup : ItemDetailsLookup<String?>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<String?>? {
            b.rv.findChildViewUnder(e.x, e.y)?.let {
                val h = b.rv.getChildViewHolder(it)
                if (h is ListSvd.ViewHolder) return@getItemDetails h.getItemDetails()
            }
            return null
        }
    }

    inner class SelectObserver : SelectionTracker.SelectionObserver<String>() {
        override fun onSelectionChanged() {
            super.onSelectionChanged()
            // TODO: USE tracker.hasSelection() AND ETC
        }
    }
}
