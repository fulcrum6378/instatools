package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.Inquisitor
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.data.Unfollower
import ir.mahdiparastesh.instatools.databinding.PageUnfBinding
import ir.mahdiparastesh.instatools.list.ListUnf
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.UiTools

class PageUnf(c: Main) : BasePage(c) {
    lateinit var b: PageUnfBinding
    override var handler: Handler? = null

    companion object {
        var theHandler: Handler? = null
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageUnfBinding.inflate(
            c.themeInflater(BaseActivity.Theme.PRIMARY, inf), parent, false
        )
        if (Main.guest) {
            guestMode(b.root, BaseActivity.Theme.PRIMARY); return b.root; }

        theHandler = object : Handler(Looper.getMainLooper()) {
            @SuppressLint("NotifyDataSetChanged")
            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    Action.LOADED.ordinal -> (msg.obj as List<Unfollower>).apply {
                        c.m.unfollowers = ArrayList(this)
                        c.m.unfollowers!!.sortBy { it.followedBy }
                        if (isNullOrEmpty()) fetch() else adapt()
                    }
                    Action.ANALYSED.ordinal -> if (c.m.unfollowers != null) {
                        c.m.unfollowers!!.add(msg.obj as Unfollower)
                        b.rv.adapter?.notifyItemInserted(c.m.unfollowers!!.size - 1)
                    }
                    /*Unfollower.find(msg.obj as Unfollower, c.m.unfollowers!!)?.let {
                            b.rv.adapter?.notifyItemInserted(it)
                            if (it > 0) b.rv.adapter?.notifyItemRangeChanged(0, it)
                            if (it < c.m.unfollowers!!.size - 1)
                                b.rv.adapter?.notifyItemRangeChanged(
                                    it + 1, c.m.unfollowers!!.size - 1
                                )
                        }*/
                    Action.COMPLETED.ordinal -> {
                        c.m.unfollowers!!.sortBy { it.followedBy }
                        b.rv.adapter?.notifyDataSetChanged()
                        fetching = false
                        b.refresher.isRefreshing = false
                    }
                    Action.ABORTED.ordinal -> {
                        fetching = false
                        b.refresher.isRefreshing = false
                        // TODO: ALERT USER
                    }
                }
            }
        }
        handler = theHandler

        b.refresher.setOnRefreshListener { fetch() }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
            }
        })
        if (c.m.unfollowers != null) adapt()
        else Thread {
            handler?.obtainMessage(Action.LOADED.ordinal, c.pDao.unfollowers())?.sendToTarget()
        }.start()

        return b.root
    }

    private fun fetch() {
        if (fetching) return
        fetching = true
        c.m.unfollowers = arrayListOf()
        c.pDao.deleteUnfollowers()
        adapt()
        c.startService(Intent(c, Inquisitor::class.java))
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

    enum class Action { LOADED, ANALYSED, COMPLETED, ABORTED }
}
