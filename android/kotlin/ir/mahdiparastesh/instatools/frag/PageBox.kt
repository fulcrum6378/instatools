package ir.mahdiparastesh.instatools.frag

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.view.forEachIndexed
import androidx.core.view.get
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Dm
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.api.Rest.InboxPage
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.DmNotSeenBinding
import ir.mahdiparastesh.instatools.databinding.ExportOptionsBinding
import ir.mahdiparastesh.instatools.databinding.PageBoxBinding
import ir.mahdiparastesh.instatools.job.Exporter
import ir.mahdiparastesh.instatools.list.ListBox
import ir.mahdiparastesh.instatools.list.ListThd
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.util.BasePageMain
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools.areEnabled
import ir.mahdiparastesh.instatools.view.UiTools.enabled
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageBox : BasePageMain(BaseActivity.Theme.TERTIARY), ActivityResultCallback<ActivityResult> {
    lateinit var b: PageBoxBinding
    private val pickle: Pickle by lazy {
        Pickle(c.cacheDir, c.m.acc!!.id, Pickle.Type.DIRECT, null)
    }
    private var exportable: Exportable? = null
    private var guideDmNotSeenShowing = false
    private var boxScroll: Int? = null
    private var dirFileProblem = false

    override val root: ConstraintLayout get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val emptyIcon: Int = R.drawable.done_box
    override val expandable: Expandable by lazy {
        Expandable(
            c, b.expanded, c.color(if (!c.night()) R.color.defBG else R.color.CT)
        ) { updateShadow() }
    }
    override val selectiveMenuRes: Int? = null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.mm.dmInbox != null
    override fun isModelEmpty(): Boolean =
        if (c.mm.dmThread == null)
            c.mm.dmInbox?.threads?.isEmpty() == true
        else
            c.mm.dmThread?.items?.isEmpty() == true

    override fun createAdapter(): RecyclerView.Adapter<*> =
        if (c.mm.dmThread == null) ListBox(c, this) else ListThd(c, this)

    override fun reuseAdapter(): Boolean = false

    override fun canLoadMore(): Boolean =
        if (c.mm.dmThread == null)
            c.mm.dmInbox?.has_older != false
        else
            c.mm.dmThread!!.has_older

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageBoxBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun shouldShowJumper(): Boolean =
        if (c.mm.dmThread == null)
            super.shouldShowJumper()
        else
            false

    override fun canRefresh(): Boolean =
        super.canRefresh() && c.mm.dmThread == null

    override suspend fun fetch(reset: Boolean) {
        if (c.mm.dmThread == null) { // on ListBox

            // first read from cache if available
            val cache =
                if (c.mm.dmInbox == null && !reset) pickle.restore<Dm.Inbox>()
                else null
            if (cache != null) {
                c.mm.dmInbox = cache
                withContext(Dispatchers.Main) { onLoaded() }
                return; }

            // fetch the online inbox
            val page = Api.json<InboxPage>(
                Api.Endpoint.INBOX.url.format(c.mm.dmInbox?.oldest_cursor ?: "")
            )

            // update the data model and the UI
            if (c.mm.dmInbox == null || reset) {
                c.mm.dmInbox = page.inbox
                withContext(Dispatchers.Main) { onLoaded() }
            } else c.mm.dmInbox?.apply {
                val lastBefore = threads.size
                threads.removeAll { it.thread_id in page.inbox.threads.map { t -> t.thread_id } }
                threads.addAll(page.inbox.threads)
                threads.sortByDescending { it.last_activity_at }
                oldest_cursor = page.inbox.oldest_cursor
                has_older = page.inbox.has_older
                withContext(Dispatchers.Main) {
                    onLazilyLoaded(lastBefore, page.inbox.threads.size)
                }
            }

            // cache the data model
            c.mm.dmInbox?.also { pickle.save(it) }

        } else { // on ListThd
            val dmThd = Api.json<Rest.InboxThread>(
                Api.Endpoint.DIRECT.url
                    .format(c.mm.dmThread!!.thread_id, c.mm.dmThread!!.items.first().item_id, 20)
            ).thread
            if (dmThd == null) {
                withContext(Dispatchers.Main) { onLazilyFailed(-5) }
                return; }

            val bef = c.mm.dmThread!!.items.size
            c.mm.dmThread!!.items.removeAll { // TODO costly operation
                it.item_id in dmThd.items.map { t -> t.item_id }
            }
            c.mm.dmThread!!.items.addAll(dmThd.items)
            c.mm.dmThread!!.has_older = dmThd.has_older
            c.mm.dmThread!!.items.sortBy { it.timestamp }
            val dif = c.mm.dmThread!!.items.size - bef
            withContext(Dispatchers.Main) { onLazilyLoaded(0, dif) }
        }
    }

    override fun onLoaded() {
        val prevScrollPos = boxScroll
        super.onLoaded()
        if (c.mm.dmThread == null) {
            prevScrollPos?.also { b.rv.scrollToPosition(it) }

            if (!canLoadMore()) c.mm.dmInboxCount.value = c.mm.dmInbox?.threads?.size ?: 0
        }

        // teach the user that they can view messages without them being marked as seen
        if (!isModelEmpty() && !c.gsp.getBoolean(Settings.spLearntDmNotSeen, false)
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

    override fun onLazilyLoaded(start: Int, size: Int) {
        super.onLazilyLoaded(start, size)
        if (c.mm.dmThread == null) {
            if (!canLoadMore()) c.mm.dmInboxCount.value = c.mm.dmInbox?.threads?.size ?: 0
        } else {
            b.rv.adapter?.notifyItemRangeChanged(size, c.mm.dmThread!!.items.size)
        }
    }

    fun expOptions(method: Exporter.Method, thread: Dm.DmThread) {
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
                b.jumper.vis(true)
                expandable.collapse(); return true; }
            c.mm.dmThread = null
            onLoaded()
            return true; }
        return false
    }
}
