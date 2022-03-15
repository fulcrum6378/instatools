package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.NetworkResponse
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
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePageMain
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageBox : BasePageMain(), ActivityResultCallback<ActivityResult> {
    private lateinit var b: PageBoxBinding
    private var boxThread: FetchOfInbox? = null
    var thdThread: FetchOfThread? = null
    private var exportable: Exportable? = null

    override val com: PageCompanion = Companion
    override lateinit var inflater: LayoutInflater
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout get() = b.root
    override val selectiveMenuRes: Int? = null
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_FETCHED to { msg ->
            if (c.m.dmThread == null) {
                onLoaded(c.m.dmInbox?.threads.isNullOrEmpty())
                if (c.m.dmInbox?.has_older == true && !b.rv.canScrollVertically(1)
                ) boxThread = FetchOfInbox().also { it.start() }
            } else if (msg.obj != null) {
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
        },
        HANDLE_ABORTED to { onFailed(c.getString(R.string.loadFailed)) },
        Api.HANDLE_ERROR to {
            onFailed(
                c.getString(
                    R.string.unknownError, (it.obj as NetworkResponse?)?.statusCode.toString()
                )
            )
        }
    )

    companion object : PageCompanion()

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        inflater = c.themeInflater(BaseActivity.Theme.TERTIARY, inf)
        b = PageBoxBinding.inflate(inflater, parent, false)
        if (Main.guest) {
            guestMode(b.root, BaseActivity.Theme.TERTIARY); return b.root; }
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (Main.guest) return
        super.onViewCreated(view, savedInstanceState)

        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback c.m.dmThread != null
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (c.m.dmThread == null) {
                    if (!b.rv.canScrollVertically(1) &&
                        boxThread?.active != true && c.m.dmInbox?.has_older != false
                    ) boxThread = FetchOfInbox().also { it.start() }
                } else {
                    if (thdThread?.active != true &&
                        c.m.dmThread!!.has_older && !b.rv.canScrollVertically(-1)
                    ) thdThread = FetchOfThread(
                        c, c.m.dmThread!!.thread_id, c.m.dmThread!!.items.first().item_id, handler
                    ).also { it.start() }
                }
            }
        })

        if (c.m.dmInbox != null) onLoaded(c.m.dmInbox?.threads.isNullOrEmpty())
        else if (boxThread?.active != true) boxThread = FetchOfInbox().also { it.start() }
    }

    override fun onRefresh() {
        if (boxThread?.active == true) return
        b.rv.adapter = null
        c.m.dmInbox = null
        boxThread = FetchOfInbox().also { it.start() }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean, asGuest: Boolean) {
        super.onLoaded(isEmpty, asGuest)
        if (c.m.dmThread == null) {
            if (b.rv.adapter == null || b.rv.adapter !is ListBox)
                b.rv.adapter = ListBox(c, this)
            else b.rv.adapter?.notifyDataSetChanged()
        } else {
            if (b.rv.adapter == null || b.rv.adapter !is ListThd)
                b.rv.adapter = ListThd(c, this)
            else b.rv.adapter?.notifyDataSetChanged()
        }
        updateJumper()
    }

    override fun updateJumper() {
        if (c.m.dmThread == null) super.updateJumper()
        else if (shouldShowJumper.value == true) shouldShowJumper.value = false
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
                c.exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = method.mime
                    putExtra(Intent.EXTRA_TITLE, "Exported $userName.${method.ext}")
                })
                activityResulted = false
                c.loadInterstitial("ca-app-pub-9457309151954418/8317918650") {
                    !c.showingAd && activityResulted
                }
            }
        }.show().stylise(c)
    }

    private var activityResulted = false
    override fun onActivityResult(result: ActivityResult) {
        if (result.data?.data == null || exportable == null) {
            exportable = null; return; }
        exportable!!.uri = result.data!!.data!!.toString()
        CoroutineScope(Dispatchers.IO).launch {
            c.dao.addExportable(exportable!!)
            withContext(Dispatchers.Main) { c.startService(Intent(c, Exporter::class.java)) }
        }
        c.showInterstitial()
        activityResulted = true
    }

    override fun goBack(): Boolean {
        if (c.m.dmThread != null) {
            c.m.dmThread = null
            onLoaded(c.m.dmInbox?.threads.isNullOrEmpty())
            return true
        }
        return false
    }

    inner class FetchOfInbox : BaseThread() {
        override fun run() {
            super.run()
            Api<InboxPage>(
                c, Api.Type.INBOX.url.format(c.m.dmInbox?.oldest_cursor ?: ""),
                InboxPage::class, handler, onError = { interrupt() }
            ) { page ->
                if (c.m.dmInbox == null) c.m.dmInbox = page.inbox
                else {
                    c.m.dmInbox?.threads?.addAll(page.inbox.threads)
                    c.m.dmInbox?.threads?.sortByDescending { it.last_activity_at }
                    c.m.dmInbox?.oldest_cursor = page.inbox.oldest_cursor
                    c.m.dmInbox?.has_older = page.inbox.has_older
                }
                handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                interrupt()
            }
        }
    }

    class FetchOfThread(
        val c: Persistent, private val threadId: String, private val oldestId: String,
        val handler: Handler?
    ) : BaseThread() {
        override fun run() {
            super.run()
            Api<Rest.InboxThread>(
                c, Api.Type.DIRECT.url.format(threadId, oldestId), Rest.InboxThread::class,
                handler, onError = { interrupt() }
            ) { inbox ->
                if (inbox.status == "ok")
                    handler?.obtainMessage(HANDLE_FETCHED, inbox.thread)?.sendToTarget()
                interrupt()
            }
        }
    }
}
