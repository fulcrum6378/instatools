package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.NetworkResponse
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.databinding.DmNotSeenBinding
import ir.mahdiparastesh.instatools.databinding.ExportOptionsBinding
import ir.mahdiparastesh.instatools.databinding.PageBoxBinding
import ir.mahdiparastesh.instatools.json.Api
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
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
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
    val expandable: Expandable by lazy {
        Expandable(
            c, b.expanded, handler, c.color(if (!c.night()) R.color.defBG else R.color.CT)
        ) { updateShadow() }
    }
    var voicePlayer: MediaPlayer? = null

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
        },
        Expandable.HANDLE_EXPANDABLE_ERROR to {
            try {
                Snackbar.make(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG).show()
            } catch (ignored: IllegalArgumentException) {
            }
        },
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

        // DM won't be Seen Guide
        if (!asGuest && !isEmpty && !c.gsp.getBoolean(Settings.spLearntDmNotSeen, false)
            && !guideDmNotSeenShowing
        ) AlertDialog.Builder(c).apply {
            guideDmNotSeenShowing = true
            val bn = DmNotSeenBinding.inflate(inflater)
            bn.desc.typeface = c.fontRegular
            setTitle(R.string.dmNotSeen)
            setView(bn.root)
            setPositiveButton(R.string.ok) { _, _ ->
                guideDmNotSeenShowing = false
                c.gsp.edit().putBoolean(Settings.spLearntDmNotSeen, true).apply()
            }
        }.show().stylise(c)
    }

    override fun updateShadow() {
        if (bInitialised)
            c.b.tbShadow.vish(rv().computeVerticalScrollOffset() > 0 && !expandable.zoomed)
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
        val bi = ExportOptionsBinding.inflate(inflater, null, false)
        bi.ll.forEach { ch ->
            when (ch) {
                is MaterialCheckBox -> ch.typeface = c.fontRegular
                is RadioGroup -> ch.forEach { (it as MaterialRadioButton).typeface = c.fontRegular }
            }
        }
        val opt = c.sp?.getString(Settings.spExpOptions, null)
            ?.let { Exportable.Options.parse(it) } ?: Exportable.Options()
        bi.incImage.isChecked = opt.img()
        if (opt.img()) bi.quaImage.check(Exportable.Options.quaImage[opt.image])
        bi.incVideo.isChecked = opt.vid()
        if (opt.vid()) bi.quaVideo.check(Exportable.Options.quaVideo[opt.video])
        bi.incVoice.isChecked = opt.voi()
        // TODO: MAX SLIDES

        if (!method.img) {
            bi.incImage.isChecked = false
            bi.incImage.isEnabled = false
            bi.quaImage.forEach { it.isEnabled = false }
            bi.quaImage.clearCheck()
        } else bi.incImage.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            var wasCheckedItem = bi.quaImage.checkedRadioButtonId
            override fun onCheckedChanged(v: CompoundButton, isChecked: Boolean) {
                if (isChecked) bi.quaImage.check(wasCheckedItem) else {
                    wasCheckedItem = bi.quaImage.checkedRadioButtonId
                    bi.quaImage.clearCheck()
                }
            }
        })
        if (!method.vid) {
            bi.incVideo.isChecked = false
            bi.incVideo.isEnabled = false
            bi.quaVideo.forEach { it.isEnabled = false }
            bi.quaVideo.clearCheck()
            bi.incVoice.isChecked = false
            bi.incVoice.isEnabled = false
        } else bi.incVideo.setOnCheckedChangeListener(object :
            CompoundButton.OnCheckedChangeListener {
            var wasCheckedItem = bi.quaVideo.checkedRadioButtonId
            override fun onCheckedChanged(v: CompoundButton, isChecked: Boolean) {
                if (isChecked) bi.quaVideo.check(wasCheckedItem) else {
                    wasCheckedItem = bi.quaVideo.checkedRadioButtonId
                    bi.quaVideo.clearCheck()
                }
            }
        })

        AlertDialog.Builder(c).apply {
            setTitle(c.getString(R.string.exportOptions, method.ext.uppercase()))
            setView(bi.root)
            setNegativeButton(R.string.cancel, null)
            setPositiveButton(R.string.export) { _, _ ->
                opt.image = if (bi.incImage.isChecked)
                    Exportable.Options.quaImage.indexOf(bi.quaImage.checkedRadioButtonId) else -1
                opt.video = if (bi.incVideo.isChecked)
                    Exportable.Options.quaVideo.indexOf(bi.quaVideo.checkedRadioButtonId) else -1
                opt.voice = if (bi.incVoice.isChecked) 0 else -1
                c.sp?.edit()?.putString(Settings.spExpOptions, opt.toJson())?.apply()
                exportable = Exportable(
                    thread.thread_id, selection?.joinToString(","), method.id, opt.toJson(),
                    threadData = thread
                )
                if (!method.openTree)
                    c.exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = method.mime
                        putExtra(
                            Intent.EXTRA_TITLE,
                            "Exported ${userName}_${UiTools.fileDateTime(Persistent.now())}.${method.ext}"
                        )
                    })
                else c.exportLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
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
            if (expandable.zoomed) {
                jumper().vis(true)
                expandable.collapse(); return true; }
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
                if (!active) return@Api
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
                if (!active) return@Api
                if (inbox.status == "ok")
                    handler?.obtainMessage(HANDLE_FETCHED, inbox.thread)?.sendToTarget()
                interrupt()
            }
        }
    }
}
