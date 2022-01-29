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
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.PageBoxBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest.InboxPage
import ir.mahdiparastesh.instatools.list.ListBox
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage

class PageBox(c: Main) : BasePage(c) {
    private lateinit var b: PageBoxBinding
    private var thread: FetchSome? = null
    override var handler: Handler? = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_FETCHED -> {
                    adapt()
                    b.refresher.isRefreshing = false
                }
            }
        }
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageBoxBinding.inflate(
            c.themeInflater(BaseActivity.Theme.TERTIARY, inf),
            parent, false
        )

        if (!Main.guest) {
            b.refresher.setOnRefreshListener {
                c.m.nextDmThreads = null
                c.m.dmThreads = arrayListOf()
                if (thread?.active != true) thread = FetchSome().also { it.start() }
            }
            b.rv.viewTreeObserver.addOnScrollChangedListener {
                if ((b.rv.computeVerticalScrollExtent() + b.rv.computeVerticalScrollOffset()) ==
                    b.rv.computeVerticalScrollRange() && thread?.active != true
                ) thread = FetchSome().also { it.start() }
            }
        }
        when {
            Main.guest -> {
                // TODO: GUEST MODE
            }
            c.m.saved != null -> adapt()
            else -> thread = FetchSome().also { it.start() }

        }
        return b.root
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter == null) b.rv.adapter = ListBox(c)
        else b.rv.adapter?.notifyDataSetChanged()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        else -> false
    }

    override fun goBack(): Boolean {
        return super.goBack()
    }

    inner class FetchSome : BaseThread() {
        override fun run() {
            super.run()
            c.m.dmThreads = arrayListOf()
            /*if (c.m.nextDmThreads?.has_next_page == false) {
                b.refresher.isRefreshing = false
                return
            }*/
            Api<InboxPage>(c, Api.Type.INBOX.url, InboxPage::class, handleError = handler) { page ->
                c.m.nextDmThreads = page.inbox.next_cursor
                c.m.dmThreads?.addAll(page.inbox.threads)
                handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            }
        }
    }
}
