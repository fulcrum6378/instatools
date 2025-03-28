package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.Command
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.PageVwrBinding
import ir.mahdiparastesh.instatools.databinding.PageVwrHeaderBinding
import ir.mahdiparastesh.instatools.list.ListVwr
import ir.mahdiparastesh.instatools.util.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.util.BasePageViewer
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.AnyViewHolder
import ir.mahdiparastesh.instatools.view.GlideShimmer
import ir.mahdiparastesh.instatools.view.EasyPopupMenu
import ir.mahdiparastesh.instatools.view.PostSelector
import ir.mahdiparastesh.instatools.view.SafeGridManager
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageVwr : BasePageViewer(), PostSelector {
    lateinit var b: PageVwrBinding
    private var showPv: Boolean = false

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = null
    override val jumper: ImageView? get() = b.jumper
    override var tracker: SelectionTracker<Long>? = null
    override var selectivity = false
    override val dialogContext: Context get() = c

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun shouldLoadOnPrepare(): Boolean = c.vm.user != null || c.vm.profile != null
    override fun isModelLoaded(): Boolean = c.vm.posts != null
    override fun isModelEmpty(): Boolean = c.vm.posts?.edges?.isEmpty() == true
    override fun canLoadMore(): Boolean = c.vm.posts?.page_info?.has_next_page != false

    companion object {
        var handler: Handler? = null
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageVwrBinding.inflate(inf, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // list
        (b.rv.layoutManager as SafeGridManager).spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = if (position == 0) 3 else 1
            }

        // handler
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    ForegroundService.HANDLE_ITEM_UPDATED -> {
                        val id = msg.obj as Long
                        var index = -1
                        c.vm.posts?.edges?.forEachIndexed { i, post ->
                            if (post.node.uid == id) {
                                Command.applyChangesToMedia(post.node, msg.arg1)
                                index = i
                                return@forEachIndexed; }
                        }
                        if (index == -1) return
                        gridAdapter()?.notifyItemChanged(index)

                        CoroutineScope(Dispatchers.IO).launch {
                            val pickle = Pickle(
                                c.cacheDir, c.c.acc!!.id, Pickle.Type.POSTS, c.vm.user!!.id!!
                            )
                            c.vm.posts?.also { pickle.save(it) }
                        }
                    }
                }
            }
        }

        // buttons for other pages
        b.toPageRel.setOnClickListener { if (isModelLoaded()) c.turnToPage(0) }
        b.toPageTag.setOnClickListener { if (isModelLoaded()) c.turnToPage(2) }

        showProfile()
    }

    @SuppressLint("NotifyDataSetChanged")
    @MainThread
    fun showProfile(reset: Boolean = false) {
        if (c.vm.user == null || c.vm.profile == null || !isBInitialised()) return
        onLoaded()

        // is the page private and not followed?
        showPv = c.vm.user?.pv() == true && c.vm.profile?.followed_by_viewer == false
            && c.vm.user?.username != c.c.acc?.user
        b.rv.vis(!showPv)
        if (!showPv) {
            if (reset) gridAdapter()?.notifyDataSetChanged()
            load(reset)
        }

        // update Favourite
        c.vm.fav?.also { c.c.addFavourite(it) }
    }

    override fun createAdapter(): RecyclerView.Adapter<*> =
        ConcatAdapter(Header(), ListVwr(c, this))

    override suspend fun fetch(reset: Boolean) {

        // first read from cache if available
        val pickle = Pickle(c.cacheDir, c.c.acc!!.id, Pickle.Type.POSTS, c.vm.user!!.id!!)
        val cache = if (c.vm.posts == null && !reset) pickle.restore<Page<Media>>() else null
        if (cache != null) {
            c.vm.posts = cache
            withContext(Dispatchers.Main) { onLazilyLoaded(0, cache.edges.size) }
            return; }

        // fetch online posts
        val cursor = if (!reset) c.vm.posts?.edges?.lastOrNull()?.node?.id().toString() else "null"
        val page = Api.json<GraphQl>(
            Api.Endpoint.QUERY.url,
            true, GraphQlQuery.PROFILE_POSTS.body(c.vm.user!!.username!!, "33", cursor)
        ).data!!.xdt_api__v1__feed__user_timeline_graphql_connection!!

        // update the data model and the UI
        if (c.vm.posts == null || reset) {
            c.vm.posts = page
            withContext(Dispatchers.Main) { onLazilyLoaded(0, page.edges.size) }
        } else c.vm.posts?.apply {
            val lastBefore = edges.size
            edges.addAll(page.edges)
            page_info.has_next_page = page.page_info.has_next_page
            withContext(Dispatchers.Main) { onLazilyLoaded(lastBefore, page.edges.size) }
        }

        // cache the data model
        c.vm.posts?.also { pickle.save(it) }
    }

    override fun onLazilyLoaded(start: Int, size: Int) {
        if (size > 0) gridAdapter()?.notifyItemRangeInserted(start, size)
        if (isModelEmpty() && hasReachedBottom()) load()
    }

    fun gridAdapter() = (rv?.adapter as ConcatAdapter?)?.adapters?.get(1) as ListVwr?

    override fun onRefresh() {
        c.load(reset = true)
    }

    override fun selectionObserver(): SelectionTracker.SelectionObserver<Long>? =
        createSelectionObserver()

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtDownload ->
                enqueueSelectedMedia(c, c.vm.posts, download = true)
            R.id.vtLike ->
                enqueueSelectedMedia(c, c.vm.posts, like = true)
            R.id.vtUnlike ->
                enqueueSelectedMedia(c, c.vm.posts, unlike = true)
            R.id.vtSave ->
                enqueueSelectedMedia(c, c.vm.posts, save = true)
            R.id.vtUnsave ->
                enqueueSelectedMedia(c, c.vm.posts, unsave = true)

            R.id.vtSelectAll ->
                if (c.vm.posts?.edges != null)
                    tracker?.setItemsSelected(c.vm.posts!!.edges.map { it.node.uid }, true)
            R.id.vtDeselectAll ->
                tracker?.clearSelection()
        }
        return super.onMenuItemClick(item)
    }

    override fun selectionKeyProvider(): ItemKeyProvider<Long> =
        object : ItemKeyProvider<Long>(SCOPE_MAPPED) {
            override fun getKey(i: Int): Long? = c.vm.posts?.edges?.getOrNull(i - 1)?.node?.uid
            override fun getPosition(key: Long): Int {
                c.vm.posts?.edges?.forEachIndexed { i, edge ->
                    if (edge.node.uid == key) return@getPosition i + 1
                }
                return -1
            }
        }

    override fun goBack(): Boolean {
        return onGoBackWithSelection()
    }

    override fun onDestroy() {
        handler = null
        super.onDestroy()
    }

    inner class Header : RecyclerView.Adapter<AnyViewHolder<PageVwrHeaderBinding>>() {
        lateinit var proPic: ImageView

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):
            AnyViewHolder<PageVwrHeaderBinding> {
            val b = PageVwrHeaderBinding.inflate(c.layoutInflater, parent, false)

            // profile
            b.proPic.layoutParams = b.proPic.layoutParams
                .apply { height = c.dm.widthPixels }
            b.proClick.setOnClickListener { v ->
                val picture = c.vm.user?.originalPicture() ?: return@setOnClickListener
                EasyPopupMenu(
                    c, v, R.menu.viewer_pic_more,
                    R.id.vpDownload to {
                        CoroutineScope(Dispatchers.IO).launch {
                            c.c.downloads.add<Download>(
                                Download(
                                    Utils.PROFILE_PHOTO,
                                    Utils.now(),
                                    picture,
                                    0x1,
                                    c.vm.user!!.username!!,
                                    c.vm.user!!.biography,
                                    Utils.PROFILE.format(c.vm.user!!.username!!),
                                    c.vm.user!!.profile_pic_url,
                                    null, null, null
                                ), true
                            )
                            Downloads.initService(c)
                        }
                    }
                ).show()
            }
            proPic = b.proPicIv

            return AnyViewHolder(b)
        }

        override fun onBindViewHolder(h: AnyViewHolder<PageVwrHeaderBinding>, position: Int) {

            // profile picture
            val pic = c.vm.profile?.profile_pic_url_hd
            if (pic != null) Glide.with(c.c)
                .load(pic)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .addListener(GlideShimmer(h.b.proPic, h.b.proPicIv))
                .into(h.b.proPicIv)
            else
                h.b.proPicIv.setImageDrawable(null)

            // followers & following
            h.b.postsNum.text = c.vm.profile?.edge_owner_to_timeline_media?.toString()
                ?: getString(R.string.vwFlwZero)
            h.b.followersNum.text = c.vm.profile?.edge_followed_by?.toString()
                ?: getString(R.string.vwFlwZero)
            h.b.followingNum.text = c.vm.profile?.edge_follow?.toString()
                ?: getString(R.string.vwFlwZero)

            val showPv_ = showPv && c.vm.profile != null
            h.b.privateAcc.vis(showPv_)
            if (showPv_) {
                h.b.privateAcc.setCompoundDrawablesWithIntrinsicBounds(
                    null, c.drawable(
                        R.drawable.private_account, if (c.night()) R.color.defCA else null
                    )!!, null, null
                )
                h.b.privateAcc.layoutParams =
                    (h.b.privateAcc.layoutParams as ViewGroup.MarginLayoutParams).apply {
                        val vPad = ((c.dm.heightPixels.toFloat()
                            - c.dm.widthPixels.toFloat()) * 0.19f).toInt()
                        topMargin = vPad
                        bottomMargin = vPad
                    }
            }
        }

        override fun getItemCount(): Int = 1
    }
}
