package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.GridLayoutManager
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.PageSvdBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.more.BackStackOwner
import ir.mahdiparastesh.instatools.more.BaseActivity

class PageSvd(val c: Main) : Fragment(), BackStackOwner, Toolbar.OnMenuItemClickListener {
    lateinit var b: PageSvdBinding
    var tracker: SelectionTracker<String>? = null

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageSvdBinding.inflate(
            c.themeInflater(BaseActivity.Theme.SECONDARY, inf), parent, false
        )
        b.rv.layoutManager = GridLayoutManager(c, 3)
        if (!Main.guest) {
            b.refresher.setOnChildScrollUpCallback { _, _ ->
                return@setOnChildScrollUpCallback tracker?.hasSelection() == true
            }
            b.refresher.setOnRefreshListener {
                b.rv.adapter = null
                c.m.nextSaved = null
                c.m.saved = null
                fuckingIndex = 0
                fetchSome()
            }
        }
        when {
            Main.guest -> {
                // TODO: GUEST MODE
            }
            c.m.saved != null -> adapt()
            else -> {
                fuckingIndex = 0
                fetchSome()
            }
        }
        return b.root
    }

    private var fuckingMax = 5
    private var fuckingIndex = 0
    private fun fetchSome() {
        if (c.m.nextSaved?.has_next_page == false || fuckingIndex >= fuckingMax) {
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

        // TODO: THIS IS NOT A LAZY LOADING
        fuckingIndex++
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter != null) {
            b.rv.adapter?.notifyDataSetChanged()
            return; }
        b.rv.adapter = ListSvd(c, this)
        tracker = SelectionTracker.Builder(
            "saved", b.rv,
            MyItemKeyProvider(), MyDetailsLookup(),
            StorageStrategy.createStringStorage()
        ).build()
        tracker?.addObserver(SelectObserver())
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.mtDownload -> if (tracker != null && c.m.saved != null) {
            /*var queued = false
            for (svd in tracker!!.selection) c.m.saved!!.find { it.id == svd }?.let { post ->
                queued = true
                c.pDao.addQueued(
                    Queued(
                        post.owner.id, post.owner.username ?: "", post.id,
                    )
                // THESE ARE NOT REAL DATA; YOU MUST FETCH REAL MEDIA ONE BY ONE!!
                )
            }
            if (queued) Downloads.initService(c)*/
            true
        } else false
        R.id.mtRemove -> {
            true
        }
        else -> false
    }

    override fun goBack(): Boolean {
        (b.rv.adapter as ListSvd?)?.let {
            if (it.zoomed) {
                it.collapse(); return@goBack true; }
        }
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return true
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
            if (tracker == null) return
            super.onSelectionChanged()
            if (!tracker!!.hasSelection()) tracker?.clearSelection()
            c.selective(tracker!!.hasSelection())
        }
    }
}
