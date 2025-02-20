package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.DownloadsBinding
import ir.mahdiparastesh.instatools.databinding.GuideSwipeDeleteBinding
import ir.mahdiparastesh.instatools.job.Downloader
import ir.mahdiparastesh.instatools.list.ListQud
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Persistent.Companion.isPathAccessible
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.Lister
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

@SuppressLint("NotifyDataSetChanged")
class Downloads : ServiceOwnerActivity(), Lister {
    lateinit var b: DownloadsBinding
    val mm: MyModel by viewModels()
    private lateinit var bd: GuideSwipeDeleteBinding
    private val handledLinks = mutableSetOf<String>()
    private val statusPlan =
        mapOf<Int, Byte>(R.id.dtRetryAll to 0, R.id.dtPauseAll to 2, R.id.dtResumeAll to 0)

    override val com: ActivityCompanion get() = Companion
    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val menuRes = R.menu.downloads_tlb
    override val controllerId = R.id.dtControl
    override val tbShadow = null
    override var shouldShowJumper: Boolean = false
    override var anJumper: ObjectAnimator? = null
    override val expandable = null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = mm.queueds != null
    override fun isModelEmpty(): Boolean = mm.queueds?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListQud(this)
    override fun screenHeight(): Int = dm.heightPixels

    class MyModel : ViewModel() {
        var queueds: CopyOnWriteArrayList<Queued>? = null
        var askedForDelete = false
    }

    companion object : ActivityCompanion() {
        const val HANDLE_INSERTED = 0
        const val HANDLE_CHANGED = 1
        const val HANDLE_DELETED = 2
        const val EXPORT_LINKS_MIME = "text/plain"
        //const val EXPORT_LINKS_EXT = "txt"

        var handler: Handler? = null

        /** It can be called from any kind of thread. */
        fun initService(c: BaseActivity) {
            val uri = c.sPreference(Settings.spStorage)
            if (uri == null || !c.c.isPathAccessible(uri)) {
                c.goTo(Settings::class) { putExtra(Settings.EXTRA_SELECT_PATH, 1) }
                return; }
            c.startService(
                Intent(c, Downloader::class.java).setAction(ForegroundService.ACTION_START)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = DownloadsBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.downloads)

        handler = object : Handler(Looper.getMainLooper()) {
            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_INSERTED -> mm.queueds?.apply {
                        add(msg.obj as Queued)
                        val pos = mm.queueds?.size ?: 1
                        b.rv.adapter?.notifyItemInserted(pos - 1)
                        if (pos > 0) b.rv.adapter?.notifyItemChanged(pos - 2)
                        updateCount((numCache ?: mm.queueds!!.size) + 1)
                    }
                    HANDLE_CHANGED -> find(msg)?.let {
                        if (it == -1) return@let
                        mm.queueds!![it] = msg.obj as Queued
                        b.rv.adapter?.notifyItemChanged(it)
                    }
                    HANDLE_DELETED -> find(msg)?.let {
                        mm.queueds!!.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        b.rv.adapter?.notifyItemRangeChanged(it, mm.queueds!!.size)
                        if (it > 0) b.rv.adapter?.notifyItemChanged(it - 1)
                        updateCount((numCache ?: mm.queueds!!.size) - 1)
                    }
                }
                updateIfEmpty(mm.queueds.isNullOrEmpty())
            }

            fun find(msg: Message): Int? =
                if (mm.queueds != null) Queued.find(msg.obj as Queued, mm.queueds!!) else null
        }

        // paste link
        b.linkButton.setOnClickListener {
            if (b.pasteLink.text.toString() == "") return@setOnClickListener
            // TODO handle link `b.pasteLink.text.toString()`
            initService(this)
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
            if (it in handledLinks || mm.queueds?.map { q -> q.link }
                    ?.let { qs -> it in qs } == true) return@also
            if (!it.startsWith(Utils.IG_OPENABLE) && !it.startsWith(Login.RAW_HOST)) {
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.downloads)
                    setMessage(R.string.nonInstagramUrl)
                    setNeutralButton(R.string.ok, null)
                }.show()
                return@also
            }
            handledLinks.add(it)
            // TODO Api.cookies
            if (m.acc != null) {
                // TODO handle link `it`
                initService(this)
            } else MaterialAlertDialogBuilder(this).apply {
                setTitle(R.string.downloads)
                setMessage(R.string.dGuestSure)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    // TODO handle link `it`
                    initService(this@Downloads)
                }
            }.show()
        }
        return super.resolveIntent(intent, false)
    }

    override fun load(reset: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            mm.queueds = CopyOnWriteArrayList(dao.queueds())
            try {
                mm.queueds!!.sortBy { it.addedAt }
            } catch (_: java.lang.UnsupportedOperationException) {
                // Mysterious error by CopyOnWriteArrayList$COWIterator.set while sorting
            }
            withContext(Dispatchers.Main) { onLoaded() }
        }
    }

    override fun onLoaded() {
        super.onLoaded()
        if (!isModelEmpty()) onStateChanged(true)
        updateCount(mm.queueds!!.size)
    }

    override fun onResume() {
        super.onResume()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(Notify.ID_DOWNLOADER_ERROR)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)
        Downloader.active.observe(this) {
            updateControlButton(it)
            b.rv.adapter?.notifyDataSetChanged()
            updateCount(mm.queueds!!.size)
        }
        return ret
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.dtControl -> if (!mm.queueds.isNullOrEmpty()) {
                if (Downloader.active.value == true) stopService(Intent(c, Downloader::class.java)
                    .apply { action = ForegroundService.ACTION_STOP })
                else initService(this@Downloads)
                b.rv.adapter?.notifyDataSetChanged()
            }

            R.id.dtRetryAll, R.id.dtPauseAll, R.id.dtResumeAll ->
                if (!mm.queueds.isNullOrEmpty()) CoroutineScope(Dispatchers.IO).launch {
                    var any = false
                    mm.queueds?.forEach {
                        if (it.status == statusPlan[item.itemId] ||
                            !(item.itemId == R.id.dtRetryAll || it.status != 1.toByte()) ||
                            (item.itemId == R.id.dtRetryAll && it.status == 2.toByte())
                        ) return@forEach
                        it.status = statusPlan[item.itemId]!!
                        dao.updateQueued(it)
                        any = true
                    }
                    if (any) {
                        withContext(Dispatchers.Main) { b.rv.adapter?.notifyDataSetChanged() }
                        if (item.itemId != R.id.dtPauseAll) initService(this@Downloads)
                    }
                } else Toast.makeText(c, R.string.dEmptyQueue, Toast.LENGTH_SHORT).show()

            R.id.dtExportLinks -> {}/*if (!mm.queueds.isNullOrEmpty())
                exportLinks.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = EXPORT_LINKS_MIME
                    putExtra(
                        Intent.EXTRA_TITLE,
                        "instatools_links_${Utils.fileDateTime(Persistent.now())}.$EXPORT_LINKS_EXT"
                    )
                }) else Toast.makeText(c, R.string.dEmptyQueue, Toast.LENGTH_SHORT).show()*/

            R.id.dtImportLinks -> {}/*importLinks.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = EXPORT_LINKS_MIME
            })*/

            R.id.dtClearAll -> if (!mm.queueds.isNullOrEmpty())
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.listClear)
                    setMessage(R.string.listClearSure)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            dao.deleteQueueds()
                            mm.queueds?.clear()
                            b.rv.adapter?.notifyDataSetChanged()
                            updateCount(mm.queueds!!.size)
                        }
                    }
                }.show() else Toast.makeText(c, R.string.dEmptyQueue, Toast.LENGTH_SHORT).show()
        }
        return super.onMenuItemClick(item)
    }

    /*private val exportLinks = launcherForResult {
        if (it.resultCode == RESULT_OK && mm.queueds != null) CoroutineScope(Dispatchers.IO).launch {
            try {
                contentResolver.openFileDescriptor(it.data!!.data!!, "w")?.use { des ->
                    FileOutputStream(des.fileDescriptor).use { fos ->
                        fos.write(mm.queueds!!.joinToString("\n") { q -> q.link }
                            .encodeToByteArray())
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
    private val importLinks = launcherForResult {
        if (it.resultCode == RESULT_OK) CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                contentResolver.openFileDescriptor(it.data!!.data!!, "r").use { des ->
                    FileInputStream(des!!.fileDescriptor).readBytes().toString(Charsets.UTF_8)
                        .split("\n")
                }
            }.onSuccess { links ->
                val curLinks = mm.queueds?.map { q -> q.link } ?: listOf()
                for (l in links) if (l !in curLinks)
                    dao.addQueued(Queued(Persistent.now(), l))
                handler?.obtainMessage(HANDLE_RESET, 1, 0, dao.queueds())?.sendToTarget()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        c, R.string.importReadError, Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }*/

    private var isSwipeDeleteInflated: Boolean? = false
    override fun onStateChanged(hasContent: Boolean) {
        super.onStateChanged(hasContent)

        // teach users that they can swipe items in order to delete them
        if (isSwipeDeleteInflated == null) return
        if (!gsp.getBoolean(Settings.spLearntSwipeDelete, false)) when {
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

    override fun updateShadow() {
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        @Suppress("DEPRECATION") super.onBackPressed()
        if (isTaskRoot) goTo(Main::class)
    }

    override fun onDestroy() {
        mm.queueds = null
        super.onDestroy()
    }

    inner class SwipeToRemove : ItemTouchHelper.Callback() {
        override fun getMovementFlags(rv: RecyclerView, h: RecyclerView.ViewHolder): Int =
            makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)

        override fun onMove(
            rv: RecyclerView, h: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(h: RecyclerView.ViewHolder, direction: Int) {
            val q = mm.queueds?.getOrNull(h.layoutPosition) ?: return
            if (!mm.askedForDelete) MaterialAlertDialogBuilder(this@Downloads).apply {
                setTitle(R.string.downloads)
                setMessage(R.string.deleteItemSure)
                setCancelable(false)
                setPositiveButton(R.string.yes) { _, _ ->
                    mm.askedForDelete = true
                    delete(q)
                    Delay(30000L) { mm.askedForDelete = false }
                }
                setNegativeButton(R.string.no, null)
            }.show()
            else delete(q)
        }

        private fun delete(q: Queued) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dao.deleteQueued(q)
                } catch (_: Exception) {
                }
                if (mm.queueds != null) withContext(Dispatchers.Main) {
                    Queued.find(q, mm.queueds)?.let {
                        mm.queueds?.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        if (mm.queueds == null) return@let
                        b.rv.adapter?.notifyItemRangeChanged(it, mm.queueds!!.size - 1)
                        if (it > 0) b.rv.adapter?.notifyItemChanged(it - 1)
                        updateCount((numCache ?: mm.queueds!!.size) - 1)
                    }
                }
                handler?.obtainMessage(-1)?.sendToTarget()
            }
            if (isSwipeDeleteInflated != null) {
                b.root.removeView(bd.root)
                gsp.edit { putBoolean(Settings.spLearntSwipeDelete, true) }
                isSwipeDeleteInflated = null
            }
        }
    }
}
