package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.DownloadsBinding
import ir.mahdiparastesh.instatools.databinding.GuideSwipeDeleteBinding
import ir.mahdiparastesh.instatools.list.ListQud
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.Persistent.Companion.isPathAccessible
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

@SuppressLint("NotifyDataSetChanged")
class Downloads : ServiceOwnerActivity() {
    lateinit var b: DownloadsBinding
    private lateinit var bd: GuideSwipeDeleteBinding
    private val handledLinks = mutableSetOf<String>()
    val mm: MyModel by viewModels()
    private val statusPlan =
        mapOf<Int, Byte>(R.id.dtRetryAll to 0, R.id.dtPauseAll to 2, R.id.dtResumeAll to 0)

    override val menuRes = R.menu.downloads_tlb
    override val com: ActivityCompanion get() = Companion
    override val controllerId = R.id.dtControl

    class MyModel : ViewModel() {
        var queueds: CopyOnWriteArrayList<Queued>? = null
        var askedForDelete = false
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
                    }
                    HANDLE_DELETED -> find(msg)?.let {
                        mm.queueds!!.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        b.rv.adapter?.notifyItemRangeChanged(it, mm.queueds!!.size)
                        if (it > 0) b.rv.adapter?.notifyItemChanged(it - 1)
                    }
                    HANDLE_CHANGED -> find(msg)?.let {
                        if (it == -1) return@let
                        mm.queueds!![it] = msg.obj as Queued
                        b.rv.adapter?.notifyItemChanged(it)
                    }
                    HANDLE_RESET -> {
                        if (msg.arg1 == 1) mm.queueds =
                            CopyOnWriteArrayList(msg.obj as List<Queued>)
                        if (b.rv.adapter == null) b.rv.adapter = ListQud(this@Downloads)
                        else b.rv.adapter?.notifyDataSetChanged()
                    }
                    HANDLE_429 -> MaterialAlertDialogBuilder(this@Downloads).apply {
                        setTitle(R.string.downloads)
                        setMessage(R.string.queuer429)
                        setNeutralButton(R.string.ok, null)
                    }.show()
                }
                updateIfEmpty(mm.queueds.isNullOrEmpty())
            }

            fun find(msg: Message): Int? =
                if (mm.queueds != null) Queued.find(msg.obj as Queued, mm.queueds!!) else null
        }

        // Paste link
        b.linkButton.setOnClickListener {
            if (b.pasteLink.text.toString() == "") return@setOnClickListener
            initService(this, b.pasteLink.text.toString())
            b.pasteLink.setText("")
        }
        if (!night()) color(R.color.CS).apply {
            b.pasteLink.setTextColor(this)
            b.pasteLink.setHintTextColor(Color.argb(100, red, green, blue))
        }

        // Load data
        CoroutineScope(Dispatchers.IO).launch {
            mm.queueds = CopyOnWriteArrayList(dao.queueds())
            try {
                mm.queueds!!.sortBy { it.addedAt }
            } catch (e: java.lang.UnsupportedOperationException) {
                // Mysterious error by CopyOnWriteArrayList$COWIterator.set while sorting
            }
            handler?.obtainMessage(HANDLE_RESET)?.sendToTarget()
            withContext(Dispatchers.Main) {
                if (mm.queueds!!.isNotEmpty() == defaultState) onStateChanged(true)
            }
        }

        // Jumper
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateJumper()
            }
        })
        b.jumper.setOnClickListener { b.rv.smoothScrollToPosition(0) }
        b.jumper.translationY = UiTools.jumperTrans(this)
        shouldShowJumper.observe(this) {
            anJumper?.cancel()
            anJumper = UiTools.anJumper(this, b.jumper, it)
        }

        // More
        ItemTouchHelper(SwipeToRemove()).attachToRecyclerView(b.rv)
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.also {
            if (it in handledLinks || mm.queueds?.map { q -> q.link }
                    ?.let { qs -> it in qs } == true) return@also
            if (!it.startsWith(UiTools.IG_OPENABLE) && !it.startsWith("https://instagram.com/")) {
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.downloads)
                    setMessage(R.string.nonInstagramUrl)
                    setNeutralButton(R.string.ok, null)
                }.show()
                return@also
            }
            handledLinks.add(it)
            if (!Main.guest) initService(this, it)
            else MaterialAlertDialogBuilder(this).apply {
                setTitle(R.string.downloads)
                setMessage(R.string.dGuestSure)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ -> initService(this@Downloads, it) }
            }.show()
        }
        return super.resolveIntent(intent, false)
    }

    override fun onResume() {
        super.onResume()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(Notify.ID_QUEUER_SOME_FAILED)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)
        Queuer.active.observe(this) {
            updateControlButton(it)
            if (it) handler?.obtainMessage(HANDLE_RESET)?.sendToTarget()
        }
        return ret
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.dtControl -> if (!mm.queueds.isNullOrEmpty()) {
                if (Queuer.active.value!!) stopService(Intent(c, Queuer::class.java)
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
                    if (any) withContext(Dispatchers.Main) {
                        b.rv.adapter?.notifyDataSetChanged()
                        if (item.itemId != R.id.dtPauseAll) initService(this@Downloads, "")
                    }
                } else Toast.makeText(c, R.string.dEmptyQueue, Toast.LENGTH_SHORT).show()

            R.id.dtExportLinks -> if (!mm.queueds.isNullOrEmpty())
                exportLinks.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = exportLinksMime
                    putExtra(
                        Intent.EXTRA_TITLE,
                        "instatools_links_${UiTools.fileDateTime(Persistent.now())}.$exportLinksExt"
                    )
                }) else Toast.makeText(c, R.string.dEmptyQueue, Toast.LENGTH_SHORT).show()

            R.id.dtImportLinks -> importLinks.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = exportLinksMime
            })

            R.id.dtClearAll -> if (!mm.queueds.isNullOrEmpty())
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.listClear)
                    setMessage(R.string.listClearSure)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            dao.deleteQueueds()
                            mm.queueds?.clear()
                            handler?.obtainMessage(HANDLE_RESET)?.sendToTarget()
                        }
                    }
                }.show() else Toast.makeText(c, R.string.dEmptyQueue, Toast.LENGTH_SHORT).show()
        }
        return super.onMenuItemClick(item)
    }

    private val exportLinks = launcherForResult {
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
                dao.addQueueds(links.map { l -> Queued(Persistent.now(), l) })
                handler?.obtainMessage(HANDLE_RESET, 1, 0, dao.queueds())?.sendToTarget()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        c, R.string.importReadError, Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private var isSwipeDeleteInflated: Boolean? = false
    override fun onStateChanged(hasContent: Boolean) {
        super.onStateChanged(hasContent)
        b.empty.vis(mm.queueds.isNullOrEmpty())

        // Swipe to Delete Guide
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

    private var shouldShowJumper = MutableLiveData(false)
    private var anJumper: ObjectAnimator? = null
    private fun updateJumper() {
        (b.rv.computeVerticalScrollOffset() > dm.heightPixels)
            .apply { if (this != shouldShowJumper.value) shouldShowJumper.value = this }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
        if (isTaskRoot) goTo(Main::class)
    }

    override fun onDestroy() {
        mm.queueds = null
        super.onDestroy()
    }

    companion object : ActivityCompanion() {
        const val HANDLE_429 = 429
        const val exportLinksMime = "text/plain"
        const val exportLinksExt = "txt"

        @MainThread
        fun initService(c: BaseActivity, link: String? = null) {
            val uri = c.sPreference(Settings.spStorage)
            if (uri == null || !c.c.isPathAccessible(uri)) {
                c.goTo(Settings::class) { putExtra(Settings.EXTRA_GIVE_LINK_BACK, link) }
                return; }
            if (Queuer.active.value!!) {
                if (!link.isNullOrBlank())
                    Queuer.handler?.obtainMessage(Queuer.HANDLE_LINK, link)?.sendToTarget()
                return
            }
            c.startService(Intent(c, Queuer::class.java).apply {
                action = ForegroundService.ACTION_START
                if (!link.isNullOrBlank()) putExtra(Queuer.EXTRA_LINK, link)
            })
        }
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
