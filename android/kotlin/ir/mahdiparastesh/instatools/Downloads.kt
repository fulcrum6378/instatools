package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Settings.Companion.isPathAccessible
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.databinding.DownloadsBinding
import ir.mahdiparastesh.instatools.databinding.GuideSwipeDeleteBinding
import ir.mahdiparastesh.instatools.job.DownloadService
import ir.mahdiparastesh.instatools.job.SimpleJobs
import ir.mahdiparastesh.instatools.list.ListQud
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.Counter
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.ServiceOwner
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream

class Downloads : BaseActivity(), ServiceOwner, Counter {
    lateinit var b: DownloadsBinding
    private val statusPlan =
        mapOf<Int, Byte>(R.id.dtRetryAll to 0, R.id.dtPauseAll to 2, R.id.dtResumeAll to 0)
    private var askedForDelete = false
    private lateinit var bd: GuideSwipeDeleteBinding
    private var isSwipeDeleteInflated: Boolean? = false

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val menuRes = R.menu.downloads_tlb
    override val tbShadow = null
    override var shouldShowJumper: Boolean = false
    override var anJumper: ObjectAnimator? = null
    override val expandable = null
    override val serviceActive = DownloadService.active
    override val controller: MenuItem? get() = b.toolbar.menu.findItem(R.id.dtControl)
    override var countBadge: BadgeDrawable? = null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = false
    override fun isModelEmpty(): Boolean = c.downloads.isEmpty<Download>()
    override fun createAdapter(): RecyclerView.Adapter<*> = ListQud(this)
    override fun screenHeight(): Int = c.dm.heightPixels

    companion object {
        const val HANDLE_CHANGED = 1
        const val HANDLE_DELETED = 2
        val exportQueueMime =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "application/octet-stream"
            else "application/json"

        var handler: Handler? = null

        /** It can be called from any kind of thread. */
        fun initService(c: BaseActivity) {
            val uri = c.c.sPreference(Settings.spStorage)
            if (uri == null || !c.c.isPathAccessible(uri)) {
                c.goTo(Settings::class) { putExtra(Settings.EXTRA_SELECT_PATH, 1) }
                return; }
            c.startService(
                Intent(c, DownloadService::class.java).setAction(ForegroundService.ACTION_START)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = DownloadsBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.downloads)

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_CHANGED ->
                        b.rv.adapter?.notifyItemChanged(msg.obj as Int)
                    HANDLE_DELETED -> {
                        val index = msg.obj as Int
                        b.rv.adapter?.notifyItemRemoved(index)
                        b.rv.adapter?.notifyItemRangeChanged(index, c.downloads.size<Download>())
                        if (index > 0) b.rv.adapter?.notifyItemChanged(index - 1)
                        onListResized()
                    }
                }
            }
        }

        // paste link
        b.linkButton.setOnClickListener {
            if (b.pasteLink.text.toString() == "") return@setOnClickListener
            handleLink(b.pasteLink.text.toString())
            b.pasteLink.setText("")
        }
        if (!night()) color(R.color.CS).apply {
            b.pasteLink.setTextColor(this)
            b.pasteLink.setHintTextColor(Color.argb(100, red, green, blue))
        }

        // list
        prepareListing(this)
        ItemTouchHelper(SwipeToRemove()).attachToRecyclerView(b.rv)
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.also {
            if (!it.startsWith(UiTools.IG_OPENABLE) && !it.startsWith(Login.RAW_HOST)) {
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.downloads)
                    setMessage(R.string.nonInstagramUrl)
                    setNeutralButton(R.string.ok, null)
                }.show()
                return@also
            }
            if (c.acc != null) {
                handleLink(it)
                initService(this)
            } else
                goTo(Login::class)
        }
        return super.resolveIntent(intent, false)
    }

    override fun onResume() {
        super.onResume()
        cancelNotifications()
    }

    private fun cancelNotifications() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).apply {
            cancel(Notify.ID_DOWNLOADER_ERROR)
            cancel(Notify.ID_DOWNLOADER_SOME_FAILED)
        }
    }

    override fun shouldShowJumper(): Boolean =
        super.shouldShowJumper() && isSwipeDeleteInflated != true

    override fun load(reset: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            c.downloads.list<Download>()
            withContext(Dispatchers.Main) { onLoaded() }
        }
    }

    override fun onLoaded() {
        super.onLoaded()

        // teach users that they can swipe items in order to delete them
        val hasContent = !isModelEmpty()
        if (isSwipeDeleteInflated == null) return
        if (!c.gsp.getBoolean(Settings.spLearntSwipeDelete, false)) when {
            isSwipeDeleteInflated == false && hasContent -> {
                b.guideSwipeDeleteStub.setOnInflateListener { _, inflated ->
                    bd = GuideSwipeDeleteBinding.bind(inflated)
                }
                b.guideSwipeDeleteStub.inflate()
                isSwipeDeleteInflated = true
            }
            isSwipeDeleteInflated == true -> bd.root.vis(hasContent)
        } else isSwipeDeleteInflated = null
    }

    override fun onListResized() {
        super.onListResized()
        updateCount(this, c.downloads.size<Download>())
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.dtControl -> if (!c.downloads.isEmpty<Download>()) {
                if (DownloadService.active.value == true) stopService(Intent(
                    c,
                    DownloadService::class.java
                )
                    .apply { action = ForegroundService.ACTION_STOP })
                else initService(this@Downloads)
                b.rv.adapter?.notifyDataSetChanged()
            }

            R.id.dtRetryAll, R.id.dtPauseAll, R.id.dtResumeAll ->
                if (!c.downloads.isEmpty<Download>()) CoroutineScope(Dispatchers.IO).launch {
                    var any = false
                    for (it in c.downloads.iterator<Download>()) {
                        if (it.status == statusPlan[item.itemId] ||
                            !(item.itemId == R.id.dtRetryAll || it.status != 1.toByte()) ||
                            (item.itemId == R.id.dtRetryAll && it.status == 2.toByte())
                        ) continue
                        it.status = statusPlan[item.itemId]!!
                        any = true
                    }
                    if (any) {
                        c.downloads.save<Download>()
                        withContext(Dispatchers.Main) { b.rv.adapter?.notifyDataSetChanged() }
                        if (item.itemId != R.id.dtPauseAll) initService(this@Downloads)
                    }
                }
                else UiTools.snackbar(b.root, R.string.dEmptyQueue, dur = Snackbar.LENGTH_SHORT)

            R.id.dtExportLinks -> if (!c.downloads.isEmpty<Download>())
                exportLinks.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = exportQueueMime
                    putExtra(
                        Intent.EXTRA_TITLE,
                        "instatools_download_list_${Utils.fileDateTime(Utils.now())}.json"
                    )
                })
            else UiTools.snackbar(b.root, R.string.dEmptyQueue, dur = Snackbar.LENGTH_SHORT)

            R.id.dtImportLinks -> importLinks.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = exportQueueMime
            })

            R.id.dtClearAll -> if (!c.downloads.isEmpty<Download>())
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.listClear)
                    setMessage(R.string.listClearSure)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            c.downloads.clear<Download>()
                            withContext(Dispatchers.Main) {
                                b.rv.adapter?.notifyDataSetChanged()
                                onListResized()
                                cancelNotifications()
                            }
                        }
                    }
                }.show()
            else UiTools.snackbar(b.root, R.string.dEmptyQueue, dur = Snackbar.LENGTH_SHORT)
        }
        return super.onMenuItemClick(item)
    }

    private fun handleLink(link: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val addition: Int
                c.downloads.addAll<Download>(
                    SimpleJobs.handlePostLink(link).queue().also { addition = it.size }, true
                )
                withContext(Dispatchers.Main) {
                    val first = c.downloads.size<Download>() - addition
                    b.rv.adapter?.notifyItemRangeInserted(first, addition)
                    if (first > 0) b.rv.adapter?.notifyItemChanged(first - 1)
                    onListResized()
                }
                initService(this@Downloads)
            } catch (e: Api.FailureException) {
                withContext(Dispatchers.Main) {
                    UiTools.snackbar(
                        b.root, UiTools.apiError(c, e.code), dur = Snackbar.LENGTH_LONG
                    )
                }
            }
        }
    }

    private val exportLinks = launcherForResult {
        if (it.resultCode == RESULT_OK) CoroutineScope(Dispatchers.IO).launch {
            contentResolver.openFileDescriptor(it.data!!.data!!, "w")!!.use { des ->
                FileOutputStream(des.fileDescriptor).use { fos ->
                    fos.write(c.downloads.export<Download>().encodeToByteArray())
                }
            }
        }
    }
    private val importLinks = launcherForResult {
        if (it.resultCode == RESULT_OK) CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                contentResolver.openFileDescriptor(it.data!!.data!!, "r").use { des ->
                    c.downloads.import<Download>(
                        FileInputStream(des!!.fileDescriptor).readBytes()
                            .toString(Charsets.UTF_8)
                    )
                }
            }.onSuccess { downloads ->
                withContext(Dispatchers.Main) { onLoaded() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    UiTools.snackbar(
                        b.root,
                        R.string.importReadError,
                        dur = Snackbar.LENGTH_LONG
                    )
                }
            }
        }
    }

    override fun updateShadow() {
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        @Suppress("DEPRECATION") super.onBackPressed()
        if (isTaskRoot) goTo(Main::class)
    }

    inner class SwipeToRemove : ItemTouchHelper.Callback() {
        override fun getMovementFlags(rv: RecyclerView, h: RecyclerView.ViewHolder): Int =
            makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)

        override fun onMove(
            rv: RecyclerView, h: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(h: RecyclerView.ViewHolder, direction: Int) {
            if (!askedForDelete) {
                val q = c.downloads.getOrNull<Download>(h.layoutPosition) ?: return
                MaterialAlertDialogBuilder(this@Downloads).apply {
                    setTitle(R.string.downloads)
                    setMessage(R.string.deleteItemSure)
                    setCancelable(false)
                    setPositiveButton(R.string.yes) { _, _ ->
                        askedForDelete = true
                        val index = c.downloads.indexOf<Download>(q)
                        if (index != -1) delete(index)
                        Delay(60000L) { askedForDelete = false }
                    }
                    setNegativeButton(R.string.no, null)
                }.show()
            } else
                delete(h.layoutPosition)
        }

        private fun delete(q: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                c.downloads.removeAt<Download>(q)

                withContext(Dispatchers.Main) {
                    b.rv.adapter?.notifyItemRemoved(q)
                    b.rv.adapter?.notifyItemRangeChanged(q, c.downloads.size<Download>() - 1)
                    if (q > 0) b.rv.adapter?.notifyItemChanged(q - 1)
                    onListResized()
                    cancelNotifications()

                    if (isSwipeDeleteInflated != null) {
                        b.root.removeView(bd.root)
                        c.gsp.edit { putBoolean(Settings.spLearntSwipeDelete, true) }
                        isSwipeDeleteInflated = null
                    }
                }
            }
        }
    }
}
