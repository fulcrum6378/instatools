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
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.DownloadsBinding
import ir.mahdiparastesh.instatools.databinding.GuideSwipeDeleteBinding
import ir.mahdiparastesh.instatools.job.DownloadService
import ir.mahdiparastesh.instatools.list.ListQud
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Persistent.Companion.isPathAccessible
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
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.io.FileOutputStream

class Downloads : BaseActivity(), ServiceOwner, Counter {
    lateinit var b: DownloadsBinding
    val pickle: Pickle by lazy {
        Pickle(c.filesDir, m.acc!!.id, Pickle.Type.DOWNLOAD_LIST, null)
    }
    private val handledLinks = mutableSetOf<String>()
    private val statusPlan =
        mapOf<Int, Byte>(R.id.dtRetryAll to 0, R.id.dtPauseAll to 2, R.id.dtResumeAll to 0)
    private var askedForDelete = false
    private lateinit var bd: GuideSwipeDeleteBinding
    private var isSwipeDeleteInflated: Boolean? = false

    override val com: ActivityCompanion get() = Companion
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
    override fun isModelEmpty(): Boolean = m.queue.isEmpty()
    override fun createAdapter(): RecyclerView.Adapter<*> = ListQud(this)
    override fun screenHeight(): Int = dm.heightPixels

    companion object : ActivityCompanion() {
        const val HANDLE_INSERTED = 0
        const val HANDLE_CHANGED = 1
        const val HANDLE_DELETED = 2
        val exportQueueMime =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "application/octet-stream"
            else "application/json"

        var handler: Handler? = null

        /** It can be called from any kind of thread. */
        fun initService(c: BaseActivity) {
            val uri = c.sPreference(Settings.spStorage)
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
            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_INSERTED -> {
                        // TODO use while handling links (change on multiple additions)
                        val pos = m.queue.size
                        b.rv.adapter?.notifyItemInserted(pos - 1)
                        if (pos > 0) b.rv.adapter?.notifyItemChanged(pos - 2)
                        onListResized()
                    }
                    HANDLE_CHANGED ->
                        b.rv.adapter?.notifyItemChanged(msg.obj as Int)
                    HANDLE_DELETED -> {
                        val index = msg.obj as Int
                        b.rv.adapter?.notifyItemRemoved(index)
                        b.rv.adapter?.notifyItemRangeChanged(index, m.queue.size)
                        if (index > 0) b.rv.adapter?.notifyItemChanged(index - 1)
                        onListResized()
                    }
                }
            }
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
            if (it in handledLinks || it in m.queue.map { q -> q.link }) return@also
            if (!it.startsWith(UiTools.IG_OPENABLE) && !it.startsWith(Login.RAW_HOST)) {
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.downloads)
                    setMessage(R.string.nonInstagramUrl)
                    setNeutralButton(R.string.ok, null)
                }.show()
                return@also
            }
            handledLinks.add(it)
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
            if (m.queue.isEmpty())
                pickle.restore<List<Queued>>()
                    ?.also { m.queue.addAll(it) }
            withContext(Dispatchers.Main) { onLoaded() }
        }
    }

    override fun onLoaded() {
        super.onLoaded()

        // teach users that they can swipe items in order to delete them
        val hasContent = !isModelEmpty()
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

    override fun onListResized() {
        super.onListResized()
        updateCount(this, m.queue.size)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.dtControl -> if (!m.queue.isEmpty()) {
                if (DownloadService.active.value == true) stopService(Intent(
                    c,
                    DownloadService::class.java
                )
                    .apply { action = ForegroundService.ACTION_STOP })
                else initService(this@Downloads)
                b.rv.adapter?.notifyDataSetChanged()
            }

            R.id.dtRetryAll, R.id.dtPauseAll, R.id.dtResumeAll ->
                if (!m.queue.isEmpty()) CoroutineScope(Dispatchers.IO).launch {
                    var any = false
                    m.queue.forEach {
                        if (it.status == statusPlan[item.itemId] ||
                            !(item.itemId == R.id.dtRetryAll || it.status != 1.toByte()) ||
                            (item.itemId == R.id.dtRetryAll && it.status == 2.toByte())
                        ) return@forEach
                        it.status = statusPlan[item.itemId]!!
                        any = true
                    }
                    if (any) {
                        pickle.save(m.queue.toList())
                        withContext(Dispatchers.Main) { b.rv.adapter?.notifyDataSetChanged() }
                        if (item.itemId != R.id.dtPauseAll) initService(this@Downloads)
                    }
                } else Toast.makeText(c, R.string.dEmptyQueue, Toast.LENGTH_SHORT).show()

            R.id.dtExportLinks -> if (!m.queue.isEmpty())
                exportLinks.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = exportQueueMime
                    putExtra(
                        Intent.EXTRA_TITLE,
                        "instatools_download_list_${Utils.fileDateTime(Utils.now())}.json"
                    )
                }) else Toast.makeText(c, R.string.dEmptyQueue, Toast.LENGTH_SHORT).show()

            R.id.dtImportLinks -> importLinks.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = exportQueueMime
            })

            R.id.dtClearAll -> if (!m.queue.isEmpty())
                MaterialAlertDialogBuilder(this).apply {
                    setTitle(R.string.listClear)
                    setMessage(R.string.listClearSure)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            m.queue.clear()
                            pickle.save(m.queue.toList())
                            b.rv.adapter?.notifyDataSetChanged()
                            onListResized()
                        }
                    }
                }.show() else Toast.makeText(c, R.string.dEmptyQueue, Toast.LENGTH_SHORT).show()
        }
        return super.onMenuItemClick(item)
    }

    private val exportLinks = launcherForResult {
        if (it.resultCode == RESULT_OK) CoroutineScope(Dispatchers.IO).launch {
            contentResolver.openFileDescriptor(it.data!!.data!!, "w")!!.use { des ->
                FileOutputStream(des.fileDescriptor).use { fos ->
                    fos.write(Json.encodeToString(m.queue).encodeToByteArray())
                }
            }
        }
    }
    private val importLinks = launcherForResult {
        if (it.resultCode == RESULT_OK) CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                contentResolver.openFileDescriptor(it.data!!.data!!, "r").use { des ->
                    Json.decodeFromString<Array<Queued>>(
                        FileInputStream(des!!.fileDescriptor).readBytes()
                            .toString(Charsets.UTF_8)
                    )
                }
            }.onSuccess { queueds ->
                m.queue.addAll(queueds)
                pickle.save(m.queue.toList())
                withContext(Dispatchers.Main) { onLoaded() }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        c, R.string.importReadError, Toast.LENGTH_LONG
                    ).show()
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
            val q = m.queue.getOrNull(h.layoutPosition) ?: return
            if (!askedForDelete) MaterialAlertDialogBuilder(this@Downloads).apply {
                setTitle(R.string.downloads)
                setMessage(R.string.deleteItemSure)
                setCancelable(false)
                setPositiveButton(R.string.yes) { _, _ ->
                    askedForDelete = true
                    delete(q)
                    Delay(60000L) { askedForDelete = false }
                }
                setNegativeButton(R.string.no, null)
            }.show()
            else delete(q)
        }

        private fun delete(q: Queued) {
            CoroutineScope(Dispatchers.IO).launch {
                pickle.save(m.queue.toList())
                withContext(Dispatchers.Main) {
                    m.findQueued(q)?.also {
                        m.queue.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        b.rv.adapter?.notifyItemRangeChanged(it, m.queue.size - 1)
                        if (it > 0) b.rv.adapter?.notifyItemChanged(it - 1)
                        onListResized()
                        cancelNotifications()
                    }
                }
            }
            if (isSwipeDeleteInflated != null) {
                b.root.removeView(bd.root)
                gsp.edit { putBoolean(Settings.spLearntSwipeDelete, true) }
                isSwipeDeleteInflated = null
            }
        }
    }
}
