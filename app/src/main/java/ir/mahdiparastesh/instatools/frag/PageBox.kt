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
import ir.mahdiparastesh.instatools.databinding.PageBoxBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.json.Rest.InboxPage
import ir.mahdiparastesh.instatools.list.ListBox
import ir.mahdiparastesh.instatools.list.ListThd
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.view.UiTools

class PageBox(c: Main) : BasePage(c) {
    private lateinit var b: PageBoxBinding
    private var boxThread: FetchInbox? = null
    var thdThread: FetchSomeDm? = null
    override var handler: Handler? = object : Handler(Looper.getMainLooper()) {
        @Suppress("UNCHECKED_CAST")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_FETCHED -> {
                    adapt()
                    b.refresher.isRefreshing = false
                }
                HANDLE_FETCHED_SOME -> if (c.m.dmThread != null) {
                    val bef = c.m.dmThread!!.items.size
                    c.m.dmThread!!.items.addAll(msg.obj as ArrayList<Dm>)
                    c.m.dmThread!!.items.sortBy { it.timestamp }
                    val dif = c.m.dmThread!!.items.size - bef
                    b.rv.adapter?.let {
                        it.notifyItemRangeInserted(0, dif)
                        it.notifyItemRangeChanged(dif, c.m.dmThread!!.items.size)
                    }
                }
                Api.HANDLE_ERROR -> b.refresher.isRefreshing = false
            }
        }
    }

    companion object {
        const val HANDLE_FETCHED_SOME = 1
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageBoxBinding.inflate(
            c.themeInflater(BaseActivity.Theme.TERTIARY, inf), parent, false
        )
        if (Main.guest) {
            guestMode(b.root, BaseActivity.Theme.TERTIARY); return b.root; }

        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback c.m.dmThread != null
        }
        b.refresher.setOnRefreshListener {
            if (boxThread?.active != true) boxThread = FetchInbox().also { it.start() }
        }
        b.rv.viewTreeObserver.addOnScrollChangedListener {
            if (c.m.dmThread != null && thdThread?.active != true &&
                c.m.dmThread!!.has_older && b.rv.computeVerticalScrollOffset() == 0
            ) thdThread = FetchSomeDm().also { it.start() }
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
            }
        })
        if (c.m.dmThreads != null) adapt()
        else boxThread = FetchInbox().also { it.start() }

        return b.root
    }

    @SuppressLint("NotifyDataSetChanged")
    fun adapt() {
        if (c.m.dmThread == null) {
            if (b.rv.adapter == null || b.rv.adapter !is ListBox)
                b.rv.adapter = ListBox(c, this)
            else b.rv.adapter?.notifyDataSetChanged()
        } else {
            if (b.rv.adapter == null || b.rv.adapter !is ListThd)
                b.rv.adapter = ListThd(c)
            else b.rv.adapter?.notifyDataSetChanged()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        else -> false
    }

    override fun updateShadow() {
        UiTools.vish(c.b.tbShadow, b.rv.computeVerticalScrollOffset() > 0)
    }

    override fun goBack(): Boolean {
        if (c.m.dmThread != null) {
            c.m.dmThread = null
            adapt()
            return true
        }
        return false
    }

    inner class FetchInbox : BaseThread() {
        override fun run() {
            super.run()
            c.m.dmThreads = arrayListOf()
            fetch()
        }

        private fun fetch() {
            Api<InboxPage>(c, Api.Type.INBOX.url, InboxPage::class, handleError = handler) { page ->
                c.m.dmThreads?.addAll(page.inbox.threads)
                if (page.inbox.has_older) Delay(500) { fetch() }
                else {
                    handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                    interrupt()
                }
            }
        }
    }

    inner class FetchSomeDm : BaseThread() {
        override fun run() {
            if (c.m.dmThread == null || !c.m.dmThread!!.has_older) {
                interrupt(); return; }
            super.run()
            Api<Rest.InboxThread>(
                c, Api.Type.DIRECT.url.format(
                    c.m.dmThread!!.thread_id, c.m.dmThread!!.items.first().item_id
                ), Rest.InboxThread::class, handleError = handler
            ) { inbox ->
                if (inbox.status == "ok")
                    handler?.obtainMessage(HANDLE_FETCHED_SOME, inbox.thread.items)?.sendToTarget()
                interrupt()
            }
        }
    }
}
