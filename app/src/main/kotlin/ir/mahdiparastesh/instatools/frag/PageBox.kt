package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.view.forEachIndexed
import androidx.core.view.get
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.NetworkResponse
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.databinding.DmNotSeenBinding
import ir.mahdiparastesh.instatools.databinding.ExportOptionsBinding
import ir.mahdiparastesh.instatools.databinding.PageBoxBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.json.Rest.InboxPage
import ir.mahdiparastesh.instatools.list.ListBox
import ir.mahdiparastesh.instatools.list.ListThd
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.more.BasePageMain
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.serv.Exporter
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.areEnabled
import ir.mahdiparastesh.instatools.view.UiTools.enabled
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageBox : BasePageMain(), ActivityResultCallback<ActivityResult> {
    private lateinit var b: PageBoxBinding
    var boxThread: FetchOfInbox? = null
    var thdThread: FetchOfThread? = null
    private var exportable: Exportable? = null
    private var guideDmNotSeenShowing = false
    private var boxScroll: Int? = null
    val reqQueue by lazy { Volley.newRequestQueue(c) }
    val expandable: Expandable by lazy {
        Expandable(
            c, b.expanded, handler, reqQueue, c.color(if (!c.night()) R.color.defBG else R.color.CT)
        ) { updateShadow() }
    }

    override val com: PageCompanion = Companion
    override val theme: BaseActivity.Theme = BaseActivity.Theme.TERTIARY
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val root: ConstraintLayout get() = b.root
    override val emptyIcon: Int = R.drawable.done_box
    override val selectiveMenuRes: Int? = null
    override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        HANDLE_FETCHED to { msg ->
            if (c.mm.dmThread == null) {
                onLoaded(c.mm.dmInbox?.threads.isNullOrEmpty())
                if (c.mm.dmInbox?.has_older == true && !b.rv.canScrollVertically(1)
                ) boxThread = FetchOfInbox().also { it.start() }
            } else if (msg.obj != null) {
                val dmThd = msg.obj as Dm.DmThread
                val bef = c.mm.dmThread!!.items.size
                c.mm.dmThread!!.items.removeAll { it.item_id in dmThd.items.map { t -> t.item_id } }
                c.mm.dmThread!!.items.addAll(dmThd.items)
                c.mm.dmThread!!.has_older = dmThd.has_older
                c.mm.dmThread!!.items.sortBy { it.timestamp }
                val dif = c.mm.dmThread!!.items.size - bef
                b.rv.adapter?.let {
                    it.notifyItemRangeInserted(0, dif)
                    it.notifyItemRangeChanged(dif, c.mm.dmThread!!.items.size)
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
        },
        Expandable.HANDLE_EXPANDABLE_ERROR to {
            UiTools.snackbar(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG, c.b.bnv)
        },
    )

    companion object : PageCompanion()

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageBoxBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Main.guest) return

        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback c.mm.dmThread != null
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (c.mm.dmThread == null) {
                    if (!b.rv.canScrollVertically(1) &&
                        boxThread?.active != true && c.mm.dmInbox?.has_older != false
                    ) boxThread = FetchOfInbox().also { it.start() }
                    boxScroll = (b.rv.layoutManager as LinearLayoutManager)
                        .findFirstCompletelyVisibleItemPosition()
                } else {
                    if (thdThread?.active != true &&
                        c.mm.dmThread!!.has_older && !b.rv.canScrollVertically(-1)
                    ) thdThread = FetchOfThread(
                        c, c.mm.dmThread!!.thread_id, c.mm.dmThread!!.items.first().item_id,
                        handler, reqQueue
                    ).also { it.start() }
                }
            }
        })

        if (c.mm.dmInbox != null) onLoaded(c.mm.dmInbox?.threads.isNullOrEmpty())
        else if (boxThread?.active != true) boxThread = FetchOfInbox().also { it.start() }
    }

    override fun onRefresh() {
        if (boxThread?.active == true) return
        b.rv.adapter = null
        c.mm.dmInbox = null
        boxThread = FetchOfInbox().also { it.start() }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLoaded(isEmpty: Boolean) {
        super.onLoaded(isEmpty)
        if (c.mm.dmThread == null) {
            val prevScrollPos = boxScroll
            if (b.rv.adapter == null || b.rv.adapter !is ListBox)
                b.rv.adapter = ListBox(c, this)
            else b.rv.adapter?.notifyDataSetChanged()
            prevScrollPos?.also { b.rv.scrollToPosition(it) }
        } else {
            if (b.rv.adapter == null || b.rv.adapter !is ListThd)
                b.rv.adapter = ListThd(c, this)
            else b.rv.adapter?.notifyDataSetChanged()
        }
        updateJumper()

        // DM won't be Seen Guide
        if (!Main.guest && !isEmpty && !c.gsp.getBoolean(Settings.spLearntDmNotSeen, false)
            && !guideDmNotSeenShowing
        ) MaterialAlertDialogBuilder(
            ContextThemeWrapper(c, R.style.Theme_InstaTools_Dialog_Tertiary)
        ).apply {
            guideDmNotSeenShowing = true
            val bn = DmNotSeenBinding.inflate(inflater)
            setTitle(R.string.dmNotSeen)
            setView(bn.root)
            setPositiveButton(R.string.ok) { _, _ ->
                guideDmNotSeenShowing = false
                c.gsp.edit { putBoolean(Settings.spLearntDmNotSeen, true) }
            }
        }.show()
    }

    override fun updateShadow() {
        if (bInitialised)
            c.b.tbShadow.vish(rv()!!.computeVerticalScrollOffset() > 0 && !expandable.zoomed)
    }

    override fun updateJumper() {
        if (c.mm.dmThread == null) super.updateJumper()
        else if (shouldShowJumper.value == true) shouldShowJumper.value = false
    }

    fun expOptions(method: Exporter.Method, thread: Dm.DmThread) {
        // selection: Array<String>? = null
        val bi = ExportOptionsBinding.inflate(inflater, null, false)
        val opt = c.sp?.getString(Settings.spExpOptions, null)
            ?.let { Exportable.Options.parse(it) } ?: Exportable.Options()
        bi.incImage.isChecked = opt.img()
        if (opt.img()) bi.quaImage.check(Exportable.Options.quaImage[opt.image])
        bi.incVideo.isChecked = opt.vid()
        if (opt.vid()) bi.quaVideo.check(Exportable.Options.quaVideo[opt.video])
        bi.incVoice.isChecked = opt.voi()

        if (!method.img) {
            bi.incImage.isChecked = false
            bi.incImage.enabled(false)
            bi.quaImage.areEnabled(false)
            bi.quaImage.clearCheck()
        } else bi.incImage.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            var wasCheckedItem = bi.quaImage.checkedRadioButtonId
            override fun onCheckedChanged(v: CompoundButton, isChecked: Boolean) {
                if (isChecked) {
                    if (wasCheckedItem == -1) wasCheckedItem =
                        Exportable.Options.quaImage[Exportable.Options.DEF_IMAGE]
                    bi.quaImage.check(wasCheckedItem)
                } else {
                    wasCheckedItem = bi.quaImage.checkedRadioButtonId
                    bi.quaImage.clearCheck()
                }
                bi.quaImage.areEnabled(isChecked)
            }
        })
        if (!method.vid) {
            bi.incVideo.isChecked = false
            bi.quaVideo.clearCheck()
            if (!method.img) {
                bi.incVideo.enabled(false)
                bi.quaVideo.areEnabled(false)
            } else {
                bi.quaVideo.forEachIndexed { i, v ->
                    if (i != 0) (v as MaterialRadioButton).enabled(false)
                    else (v as MaterialRadioButton).apply {
                        isChecked = bi.incVideo.isChecked
                        enabled(bi.incVideo.isChecked)
                    }
                }
                bi.incVideo.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) bi.quaVideo.check(Exportable.Options.quaVideo[3])
                    else bi.quaVideo.clearCheck()
                    (bi.quaVideo[0] as MaterialRadioButton).enabled(isChecked)
                }
            }
            bi.incVoice.isChecked = false
            bi.incVoice.enabled(false)
        } else bi.incVideo.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            var wasCheckedItem = bi.quaVideo.checkedRadioButtonId
            override fun onCheckedChanged(v: CompoundButton, isChecked: Boolean) {
                if (isChecked) {
                    if (wasCheckedItem == -1) wasCheckedItem =
                        Exportable.Options.quaVideo[Exportable.Options.DEF_VIDEO]
                    bi.quaVideo.check(wasCheckedItem)
                } else {
                    wasCheckedItem = bi.quaVideo.checkedRadioButtonId
                    bi.quaVideo.clearCheck()
                }
                bi.quaVideo.areEnabled(isChecked)
            }
        })
        bi.desc.setText(method.desc)

        MaterialAlertDialogBuilder(
            ContextThemeWrapper(c, R.style.Theme_InstaTools_Dialog_Tertiary)
        ).apply {
            setTitle(c.getString(R.string.exportOptions, method.ext.uppercase()))
            setView(bi.root)
            setNegativeButton(R.string.cancel, null)
            setPositiveButton(R.string.export) { _, _ ->
                opt.image = if (bi.incImage.isChecked)
                    Exportable.Options.quaImage.indexOf(bi.quaImage.checkedRadioButtonId) else -1
                opt.video = if (bi.incVideo.isChecked)
                    Exportable.Options.quaVideo.indexOf(bi.quaVideo.checkedRadioButtonId) else -1
                opt.voice = if (bi.incVoice.isChecked) 0 else -1
                c.sp?.edit { putString(Settings.spExpOptions, opt.toJson()) }
                exportable =
                    Exportable(thread.thread_id, null, method.id, opt.toJson(), threadData = thread)

                if (!method.asTree || Exporter.canCreateDirSelf(c))
                    c.exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = method.mime
                        putExtra(
                            Intent.EXTRA_TITLE,
                            "${thread.exported()}${if (!method.asTree) "." + method.ext else ""}"
                        )
                        if (method.asTree) dirFileProblem = true
                    })
                else c.exportLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                // I didn't find an easy way to exclude the non-compatible hosts.
                // In Linux/Unix, you cannot transform a directory to a file!
                // Google Drive, Dropbox and AnyDesk understand Android directory mime type is a folder.
            }
        }.show()
    }

    private var dirFileProblem = false
    override fun onActivityResult(result: ActivityResult) {
        if (result.data?.data == null || exportable == null) { // "action" and "type" are null!
            exportable = null; return; }
        if (dirFileProblem && result.data!!.data!!.authority !=
            Uri.parse(c.sPreference(Settings.spStorage)).authority
        ) {
            MaterialAlertDialogBuilder(
                ContextThemeWrapper(c, R.style.Theme_InstaTools_Dialog_Tertiary)
            ).apply {
                setTitle(R.string.exportHtml)
                setMessage(R.string.unsupportedExportUriAuth)
                setNeutralButton(R.string.ok, null)
            }.show()
            DocumentFile.fromSingleUri(c.c, result.data!!.data!!)?.delete()
            // This won't work in DropBox! But works in Google Drive and AnyDesk :)
            return; }
        exportable!!.uri = result.data!!.data!!.toString()
        CoroutineScope(Dispatchers.IO).launch {
            c.dao.addExportable(exportable!!)
            withContext(Dispatchers.Main) { c.startService(Intent(c, Exporter::class.java)) }
        }
        dirFileProblem = false
    }
    // Downloads:     "content://com.android.providers.downloads.documents/document/"
    // Internal & SD: "content://com.android.externalstorage.documents/document/"
    // Google Drive:  "content://com.google.android.apps.docs.storage/document/"
    // Dropbox:       "content://com.dropbox.product.android.dbapp.document_provider.documents/document/"
    // AnyDesk Dls:   "content://com.anydesk.anydeskandroid.documents.downloads/document/"

    override fun goBack(): Boolean {
        if (c.mm.dmThread != null) {
            if (expandable.zoomed) {
                jumper()?.vis(true)
                expandable.collapse(); return true; }
            c.mm.dmThread = null
            onLoaded(c.mm.dmInbox?.threads.isNullOrEmpty())
            return true; }
        return false
    }

    inner class FetchOfInbox : BaseThread() {
        override fun run() {
            super.run()
            reqQueue.adder = Api<InboxPage>(
                c, Api.Endpoint.INBOX.url.format(c.mm.dmInbox?.oldest_cursor ?: ""),
                InboxPage::class, handler, autoQueue = false, onError = { interrupt() }
            ) { page ->
                if (!active) return@Api
                if (c.mm.dmInbox == null) c.mm.dmInbox = page.inbox
                else c.mm.dmInbox?.apply {
                    threads.removeAll { it.thread_id in page.inbox.threads.map { t -> t.thread_id } }
                    threads.addAll(page.inbox.threads)
                    threads.sortByDescending { it.last_activity_at }
                    oldest_cursor = page.inbox.oldest_cursor
                    has_older = page.inbox.has_older
                }
                handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                interrupt()
            }
        }
    }

    class FetchOfThread(
        val c: Persistent, private val threadId: String, private val oldestId: String,
        val handler: Handler?, private val queue: RequestQueue, private val limit: Int = 20
    ) : BaseThread() {
        override fun run() {
            super.run()
            queue.adder = Api<Rest.InboxThread>(
                c, Api.Endpoint.DIRECT.url.format(threadId, oldestId, limit),
                Rest.InboxThread::class, handler, autoQueue = false, onError = { interrupt() }
            ) { inbox ->
                if (!active) return@Api
                if (inbox.status == "ok")
                    handler?.obtainMessage(HANDLE_FETCHED, inbox.thread)?.sendToTarget()
                else if (BuildConfig.DEBUG) throw Exception(inbox.status)
                interrupt()
            }
        }
    }
}
