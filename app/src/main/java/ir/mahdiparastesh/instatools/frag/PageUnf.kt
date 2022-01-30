package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.data.Unfollower
import ir.mahdiparastesh.instatools.databinding.PageUnfBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListUnf
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.UiTools

class PageUnf(c: Main) : BasePage(c) {
    lateinit var b: PageUnfBinding
    private var thread: Inquiry? = null
    override var handler: Handler? = object : Handler(Looper.getMainLooper()) {
        @Suppress("UNCHECKED_CAST")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                Action.LOADED.ordinal -> (msg.obj as List<Unfollower>).apply {
                    c.m.unfollowers = ArrayList(this)
                    c.m.unfollowers!!.sortBy { it.followedBy }
                    if (isNullOrEmpty()) fetch() else adapt()
                }
                Action.ANALYSED.ordinal ->
                    Unfollower.find(msg.obj as Unfollower, c.m.unfollowers!!)?.let {
                        b.rv.adapter?.notifyItemInserted(it)
                        if (it > 0) b.rv.adapter?.notifyItemRangeChanged(0, it)
                        if (it < c.m.unfollowers!!.size - 1)
                            b.rv.adapter?.notifyItemRangeChanged(
                                it + 1, c.m.unfollowers!!.size - 1
                            )
                    }
                Action.COMPLETED.ordinal -> {
                    fetching = false
                    b.refresher.isRefreshing = false
                }
                Api.HANDLE_ERROR -> {
                    fetching = false
                    b.refresher.isRefreshing = false
                    thread?.interrupt()
                    // TODO: ALERT USER
                }
            }
        }
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageUnfBinding.inflate(
            c.themeInflater(BaseActivity.Theme.PRIMARY, inf), parent, false
        )

        if (!Main.guest) {
            b.refresher.setOnRefreshListener { fetch() }
            b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateShadow()
                }
            })
        }
        when {
            Main.guest -> {
                // TODO: GUEST MODE (ViewStub)
            }
            c.m.unfollowers != null -> adapt()
            else -> Thread {
                handler?.obtainMessage(Action.LOADED.ordinal, c.pDao.unfollowers())?.sendToTarget()
            }.start()
        }
        return b.root
    }

    private fun fetch() {
        if (fetching) return
        fetching = true
        c.m.unfollowers = null
        c.pDao.deleteUnfollowers()
        adapt()
        thread?.interrupt()
        thread = Inquiry().also { it.start() }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter == null) b.rv.adapter = ListUnf(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        else -> false
    }

    override fun updateShadow() {
        UiTools.vish(c.b.tbShadow, b.rv.computeVerticalScrollOffset() > 0)
    }

    enum class Action { LOADED, ANALYSED, COMPLETED }

    inner class Inquiry : BaseThread() {
        private val following = arrayListOf<Rest.User>()

        override fun run() {
            super.run()
            allFollow()
        }

        private fun allFollow(next_max_id: String = "") {
            if (!active) return
            Api<Rest.Follow>(
                c, Api.Type.FOLLOWING.url.format(c.m.acc!!.id, next_max_id), Rest.Follow::class
            ) { flw ->
                following.addAll(flw.users.toMutableList())
                if (flw.next_max_id == null) {
                    c.m.unfollowers = arrayListOf()
                    analyse()
                } else Delay { allFollow(flw.next_max_id) }
            }
        }

        private fun analyse(i: Int = 0) {
            if (c.m.unfollowers == null || !active) return
            if (i >= following.size) {
                handler?.obtainMessage(Action.COMPLETED.ordinal)?.sendToTarget()
                return
            }
            Api<Profile>(
                c, Api.Type.PROFILE.url.format(following[i].username), Profile::class,
                handleError = handler
            ) { profile ->
                val u = profile.graphql?.user
                if (u == null || u.follows_viewer != false || c.m.unfollowers == null) return@Api
                val newbie = Unfollower(
                    u.id.toLong(), u.username, u.full_name, u.profile_pic_url,
                    u.edge_followed_by.count.toLong(), u.is_private == true
                )
                c.m.unfollowers!!.add(newbie)
                c.m.unfollowers!!.sortBy { it.followedBy }
                c.pDao.addUnfollower(newbie)
                handler?.obtainMessage(Action.ANALYSED.ordinal, newbie)?.sendToTarget()
            }
            Delay(500) { analyse(i + 1) }
        }
    }
}
