package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.DownloadsBinding
import ir.mahdiparastesh.instatools.databinding.GuideSwipeDeleteBinding
import ir.mahdiparastesh.instatools.list.ListQud
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Persistent.Companion.isPathAccessible
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

@SuppressLint("NotifyDataSetChanged")
class Downloads : ServiceOwnerActivity() {
    lateinit var b: DownloadsBinding
    private lateinit var bd: GuideSwipeDeleteBinding
    private val handledLinks = mutableSetOf<String>()
    val mm: MyModel by viewModels()
    // private lateinit var adBanner: AdView

    override val menuRes = R.menu.downloads_tlb
    override val com: ActivityCompanion get() = Companion
    override val controllerId = R.id.dtControl

    class MyModel : ViewModel() {
        var queueds: CopyOnWriteArrayList<Queued>? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = DownloadsBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.downloads)

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_INSERTED -> {
                        mm.queueds!!.add(msg.obj as Queued)
                        val pos = (mm.queueds?.size ?: 1)
                        b.rv.adapter?.notifyItemInserted(pos - 1)
                        if (pos > 0) b.rv.adapter?.notifyItemChanged(pos - 2)
                    }
                    HANDLE_DELETED -> {
                        /*if (m.queueds?.size in 1..5)
                            loadInterstitial(R.string.interDownloaded) {
                                m.queueds?.filter { it.isReady() }.isNullOrEmpty()
                            }*/
                        find(msg)?.let {
                            mm.queueds!!.removeAt(it)
                            b.rv.adapter?.notifyItemRemoved(it)
                            b.rv.adapter?.notifyItemRangeChanged(it, mm.queueds!!.size)
                            if (it > 0) b.rv.adapter?.notifyItemChanged(it - 1)
                        }
                    }
                    HANDLE_CHANGED -> find(msg)?.let {
                        if (it == -1) return@let
                        mm.queueds!![it] = msg.obj as Queued
                        b.rv.adapter?.notifyItemChanged(it)
                    }
                    HANDLE_RESET ->
                        if (b.rv.adapter == null) b.rv.adapter = ListQud(this@Downloads)
                        else b.rv.adapter?.notifyDataSetChanged()
                    // SHOW_AD -> showInterstitial()
                }
                updateIfEmpty(mm.queueds.isNullOrEmpty())
            }

            fun find(msg: Message): Int? =
                if (mm.queueds != null) Queued.find(msg.obj as Queued, mm.queueds!!) else null
        }

        // Paste Link
        b.linkButton.setOnClickListener {
            if (b.pasteLink.text.toString() == "") return@setOnClickListener
            initService(this, b.pasteLink.text.toString())
            b.pasteLink.setText("")
        }
        if (!night()) color(R.color.CS).apply {
            b.pasteLink.setTextColor(this)
            b.pasteLink.setHintTextColor(Color.argb(100, red, green, blue))
        }

        // More
        ItemTouchHelper(SwipeToRemove()).attachToRecyclerView(b.rv)
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.also {
            if (it in handledLinks) return@also
            if (!it.startsWith(UiTools.IG_OPENABLE) && !it.startsWith("https://instagram.com/")) {
                AlertDialog.Builder(this).apply {
                    setTitle(R.string.downloads)
                    setMessage(R.string.nonInstagramUrl)
                    setNeutralButton(R.string.ok, null)
                }.show()
                return@also
            }
            initService(this, it)
            handledLinks.add(it)
        }
        return super.resolveIntent(intent, false)
    }

    override fun onResume() {
        super.onResume()
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
    }

    /*override fun onInitializationComplete(adsInitStatus: InitializationStatus) {
        super.onInitializationComplete(adsInitStatus)
        if (!adsInitStatus.isReady()) return
        adBanner = UiTools.adaptiveBanner(this, R.string.bnrBtmDownloads)
        b.root.addView(adBanner, UiTools.adaptiveBannerLp())
        adBanner.loadAd(AdRequest.Builder().build())
        b.rv.layoutParams = (b.rv.layoutParams as ConstraintLayout.LayoutParams)
            .apply { bottomToTop = R.id.adBanner }
        b.guideSwipeDeleteStub.layoutParams =
            (b.guideSwipeDeleteStub.layoutParams as ConstraintLayout.LayoutParams)
                .apply { bottomToTop = R.id.adBanner }
    }*/

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)
        Queuer.active.observe(this) { updateControlButton(it) }
        return ret
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.dtControl -> if (!mm.queueds.isNullOrEmpty()) {
                if (Queuer.active.value!!) stopService(Intent(c, Queuer::class.java)
                    .apply { action = ForegroundService.ACTION_STOP })
                else initService(this@Downloads)
            }
            R.id.dtRetryAll -> if (mm.queueds != null) CoroutineScope(Dispatchers.IO).launch {
                var any = false
                mm.queueds?.forEach {
                    if (it.isReady()) return@forEach
                    it.status = 0.toByte()
                    dao.updateQueued(it)
                    any = true
                }
                if (any) withContext(Dispatchers.Main) {
                    b.rv.adapter?.notifyDataSetChanged()
                    initService(this@Downloads, "")
                }
            }
        }
        return super.onMenuItemClick(item)
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

    override fun onBackPressed() {
        super.onBackPressed()
        if (isTaskRoot) goTo(Main::class)
    }

    override fun onDestroy() {
        mm.queueds = null
        super.onDestroy()
    }

    companion object : ActivityCompanion() {
        // const val SHOW_AD = 5

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
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dao.deleteQueued(q)
                } catch (e: Exception) {
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
