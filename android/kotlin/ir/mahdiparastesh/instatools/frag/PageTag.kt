package ir.mahdiparastesh.instatools.frag

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.base.BasePageViewer
import ir.mahdiparastesh.instatools.base.ForegroundService
import ir.mahdiparastesh.instatools.base.PostSelector
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageTagBinding
import ir.mahdiparastesh.instatools.list.ListTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageTag : BasePageViewer(), PostSelector {
    private lateinit var b: PageTagBinding

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override var tracker: SelectionTracker<Long>? = null
    override var selectivity = false
    override val dialogContext: ContextThemeWrapper get() = c

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.vm.tagged != null
    override fun isModelEmpty(): Boolean = c.vm.tagged?.edges?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListTag(c, this)
    override fun canLoadMore(): Boolean = c.vm.tagged?.page_info?.has_next_page != false

    companion object {
        var handler: Handler? = null
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageTagBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // handler
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    ForegroundService.HANDLE_ITEM_UPDATED -> {
                        val id = msg.obj as Long
                        var index = -1
                        c.vm.tagged?.edges?.forEachIndexed { i, tag ->
                            if (tag.node.uid == id) {
                                Command.applyChangesToMedia(tag.node, msg.arg1)
                                index = i
                                return@forEachIndexed; }
                        }
                        if (index == -1) return
                        b.rv.adapter?.notifyItemChanged(index)

                        CoroutineScope(Dispatchers.IO).launch {
                            val pickle = Pickle(
                                c.cacheDir, c.c.acc!!.id, Pickle.Type.TAGGED, c.vm.profile!!.id!!
                            )
                            c.vm.tagged?.also { pickle.save(it) }
                        }
                    }
                }
            }
        }
    }

    override suspend fun fetch(reset: Boolean) {

        // first read from cache if available
        val pickle = Pickle(c.cacheDir, c.c.acc!!.id, Pickle.Type.TAGGED, c.vm.profile!!.id!!)
        val cache = if (c.vm.tagged == null && !reset) pickle.restore<Page<Media>>() else null
        if (cache != null) {
            c.vm.tagged = cache
            withContext(Dispatchers.Main) { onLoaded() }
            return; }

        // fetch online tagged posts
        val cursor = if (!reset) c.vm.tagged?.edges?.lastOrNull()?.node?.id() else null
        val page = Api.json<GraphQl>(
            Api.Endpoint.QUERY.url, true,
            if (cursor == null)
                GraphQlQuery.PROFILE_TAGGED_INITIAL.body(c.vm.profile!!.id!!, "12")
            else
                GraphQlQuery.PROFILE_TAGGED_MORE.body(c.vm.profile!!.id!!, "21", cursor)
        ).data!!.xdt_api__v1__usertags__user_id__feed_connection!!

        // update the data model and the UI
        if (c.vm.tagged == null || reset) {
            c.vm.tagged = page
            withContext(Dispatchers.Main) { onLoaded() }
        } else c.vm.tagged?.apply {
            val lastBefore = edges.size
            edges.addAll(page.edges)
            page_info.has_next_page = page.page_info.has_next_page
            withContext(Dispatchers.Main) { onLazilyLoaded(lastBefore, edges.size) }
        }

        // cache the data model
        c.vm.tagged?.also { pickle.save(it) }
    }

    override fun selectionKeyProvider() = object : ItemKeyProvider<Long>(SCOPE_MAPPED) {
        override fun getKey(i: Int): Long? = c.vm.tagged?.edges?.getOrNull(i)?.node?.uid
        override fun getPosition(key: Long): Int {
            c.vm.tagged?.edges?.forEachIndexed { i, edge ->
                if (edge.node.uid == key) return@getPosition i
            }
            return -1
        }
    }

    override fun selectionObserver(): SelectionTracker.SelectionObserver<Long>? =
        createSelectionObserver()

    override fun onSelectionStarted() {
        (b.rv.adapter as ListTag).firstLongClickSelect = true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            /*R.id.vtDownload ->
                enqueueSelectedMedia(c, c.vm.tagged, download = true)*/
            R.id.vtLike ->
                enqueueSelectedMedia(c, c.vm.tagged, like = true)
            R.id.vtUnlike ->
                enqueueSelectedMedia(c, c.vm.tagged, unlike = true)
            R.id.vtSave ->
                enqueueSelectedMedia(c, c.vm.tagged, save = true)
            R.id.vtUnsave ->
                enqueueSelectedMedia(c, c.vm.tagged, unsave = true)

            R.id.vtSelectAll ->
                if (c.vm.tagged?.edges != null)
                    tracker?.setItemsSelected(c.vm.tagged!!.edges.map { it.node.uid }, true)
            R.id.vtDeselectAll ->
                tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    override fun goBack(): Boolean {
        return onGoBackWithSelection()
    }

    override fun onDestroy() {
        handler = null
        super.onDestroy()
    }
}
