package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.PageBoxBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest.InboxPage
import ir.mahdiparastesh.instatools.list.ListBox
import ir.mahdiparastesh.instatools.more.BackStackOwner
import ir.mahdiparastesh.instatools.more.BaseActivity

class PageBox(val c: Main) : Fragment(), BackStackOwner, Toolbar.OnMenuItemClickListener {
    private lateinit var b: PageBoxBinding

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        b = PageBoxBinding.inflate(
            c.themeInflater(BaseActivity.Theme.TERTIARY, inf),
            parent, false
        )

        b.refresher.setOnRefreshListener {
            c.m.nextDmThreads = null
            c.m.dmThreads = arrayListOf()
            fetchSome()
        }
        when {
            Main.guest -> {
                // TODO: GUEST MODE
            }
            c.m.saved != null -> adapt()
            else -> {
                c.m.dmThreads = arrayListOf()
                fetchSome()
            }
        }
        return b.root
    }

    private fun fetchSome() {
        /*if (c.m.nextDmThreads?.has_next_page == false) {
            b.refresher.isRefreshing = false
            return
        }*/
        b.refresher.isRefreshing = false//
        Api<InboxPage>(c, Api.Type.INBOX.url, InboxPage::class) { page ->
            c.m.nextDmThreads = page.inbox.next_cursor
            c.m.dmThreads?.addAll(page.inbox.threads)
            adapt()
        }
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
}
