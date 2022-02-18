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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.contains
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.NetworkResponse
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.databinding.ExportOptionsBinding
import ir.mahdiparastesh.instatools.databinding.PageBoxBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.json.Rest.InboxPage
import ir.mahdiparastesh.instatools.list.ListBox
import ir.mahdiparastesh.instatools.list.ListThd
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageBox(c: Main) : BasePage(c) {
    private lateinit var b: PageBoxBinding
    private var boxThread: FetchInbox? = null
    var thdThread: FetchSomeDm? = null
    override lateinit var inflater: LayoutInflater
    override var handler: Handler? = object : Handler(Looper.getMainLooper()) {
        @Suppress("UNCHECKED_CAST")
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                HANDLE_FETCHED -> onLoaded()
                HANDLE_ABORTED -> onFailed(c.getString(R.string.loadFailed))
                HANDLE_FETCHED_SOME -> if (c.m.dmThread != null) {
                    val dmThd = msg.obj as Dm.DmThread
                    val bef = c.m.dmThread!!.items.size
                    c.m.dmThread!!.items.addAll(dmThd.items)
                    c.m.dmThread!!.has_older = dmThd.has_older
                    c.m.dmThread!!.items.sortBy { it.timestamp }
                    val dif = c.m.dmThread!!.items.size - bef
                    b.rv.adapter?.let {
                        it.notifyItemRangeInserted(0, dif)
                        it.notifyItemRangeChanged(dif, c.m.dmThread!!.items.size)
                    }
                }
                Api.HANDLE_ERROR -> onFailed(
                    c.getString(
                        R.string.unknownError, (msg.obj as NetworkResponse?)?.statusCode.toString()
                    )
                )
            }
        }
    }
    private var exportable: Exportable? = null
    private val exportLauncher =
        c.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.data?.data == null || exportable == null) {
                exportable = null; return@registerForActivityResult; }
            exportable!!.uri = it.data!!.data!!.toString()
            CoroutineScope(Dispatchers.IO).launch {
                c.dao.addExportable(exportable!!)
                withContext(Dispatchers.Main) { c.startService(Intent(c, Exporter::class.java)) }
            }
        }

    companion object {
        const val HANDLE_FETCHED_SOME = 44
        const val DELAY = 800L
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        inflater = c.themeInflater(BaseActivity.Theme.TERTIARY)
        b = PageBoxBinding.inflate(
            c.themeInflater(BaseActivity.Theme.TERTIARY, inf), parent, false
        )
        if (Main.guest) {
            b.refresher.isEnabled = false
            guestMode(b.root, BaseActivity.Theme.TERTIARY); return b.root; }

        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback c.m.dmThread != null
        }
        b.refresher.setOnRefreshListener {
            if (boxThread?.active == true) return@setOnRefreshListener
            b.rv.adapter = null
            boxThread = FetchInbox().also { it.start() }
        }
        b.rv.viewTreeObserver.addOnScrollChangedListener {
            if (c.m.dmThread != null && thdThread?.active != true &&
                c.m.dmThread!!.has_older && !b.rv.canScrollVertically(-1)
            ) thdThread = FetchSomeDm(
                c, c.m.dmThread!!.thread_id, c.m.dmThread!!.items.first().item_id, handler
            ).also { it.start() }
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
                updateJumper()
            }
        })

        //b.refresher.isRefreshing = true
        if (c.m.dmThreads != null) onLoaded()
        else if (boxThread?.active != true) boxThread = FetchInbox().also { it.start() }
        return b.root
    }

    override fun onFailed(message: String) {
        b.refresher.isRefreshing = false
        try {
            Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
        } catch (ignored: IllegalArgumentException) {
            // No suitable parent found from the given view. Please provide a valid view.
        }
        if (b.root.contains(b.loading)) {
            b.loading.animation?.cancel()
            b.root.removeView(b.loading)
        }
        if (b.rv.adapter == null) b.error.vis()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded() {
        b.refresher.isRefreshing = false
        if (b.root.contains(b.loading)) {
            b.loading.animation?.cancel()
            b.root.removeView(b.loading)
        }
        b.error.vis(false)

        if (c.m.dmThread == null) {
            if (b.rv.adapter == null || b.rv.adapter !is ListBox)
                b.rv.adapter = ListBox(c, this)
            else b.rv.adapter?.notifyDataSetChanged()
        } else {
            if (b.rv.adapter == null || b.rv.adapter !is ListThd)
                b.rv.adapter = ListThd(c, this)
            else b.rv.adapter?.notifyDataSetChanged()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        else -> false
    }

    override fun updateShadow() {
        c.b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
    }

    override fun updateJumper() {
    }

    fun expOptions(
        method: Exporter.Method,
        userName: String,
        thread: Dm.DmThread,
        selection: Array<String>? = null
    ) {
        AlertDialog.Builder(c).apply {
            setTitle(R.string.exportOptions)
            val bi = ExportOptionsBinding.inflate(inflater, null, false)
            setView(bi.root)
            setNegativeButton(R.string.cancel, null)
            setPositiveButton(R.string.export) { _, _ ->
                val opt = Exportable.Options()
                //c.sp?.edit()?.let {}
                exportable = Exportable(
                    thread.thread_id, selection?.joinToString(","), method.id, opt.toJson(),
                    threadData = thread
                )
                exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = method.mime
                    putExtra(Intent.EXTRA_TITLE, "Exported $userName.${method.ext}")
                })
            }
        }.create().show()
    }

    override fun goBack(): Boolean {
        if (c.m.dmThread != null) {
            c.m.dmThread = null
            onLoaded()
            return true
        }
        return false
    }

    inner class FetchInbox : BaseThread() {
        private var nextCursor: String? = null

        override fun run() {
            super.run()
            c.m.dmThreads = arrayListOf()
            fetch()
        }

        private fun fetch() {
            Api<InboxPage>(
                c, Api.Type.INBOX.url.format(nextCursor ?: ""), InboxPage::class, handler
            ) { page ->
                c.m.dmThreads?.addAll(page.inbox.threads)
                c.m.dmThreads?.sortByDescending { it.last_activity_at }
                nextCursor = page.inbox.oldest_cursor
                if (page.inbox.has_older) Delay(DELAY) { fetch() }
                else {
                    handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                    interrupt()
                }
            }
        }
    }

    class FetchSomeDm(
        val c: Persistent, private val threadId: String, private val oldestId: String,
        val handler: Handler?
    ) : BaseThread() {
        override fun run() {
            super.run()
            Api<Rest.InboxThread>(
                c, Api.Type.DIRECT.url.format(threadId, oldestId), Rest.InboxThread::class, handler
            ) { inbox ->
                if (inbox.status == "ok")
                    handler?.obtainMessage(HANDLE_FETCHED_SOME, inbox.thread)?.sendToTarget()
                interrupt()
            }
        }
    }
}
