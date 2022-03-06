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
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.initialization.InitializationStatus
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.DownloadsBinding
import ir.mahdiparastesh.instatools.list.ListQud
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Downloads : ServiceOwnerActivity() {
    lateinit var b: DownloadsBinding
    private lateinit var adBanner: AdView

    override val menuRes = R.menu.downloads_tlb
    override val com: ActivityCompanion get() = Companion
    override val controllerId = R.id.dtControl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = DownloadsBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.downloads)

        handler = object : Handler(Looper.getMainLooper()) {
            @SuppressLint("NotifyDataSetChanged")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_INSERTED -> {
                        m.queueds!!.add(msg.obj as Queued)
                        val pos = (m.queueds?.size ?: 1)
                        b.rv.adapter?.notifyItemInserted(pos - 1)
                        if (pos > 0) b.rv.adapter?.notifyItemChanged(pos - 2)
                    }
                    HANDLE_DELETED -> find(msg)?.let {
                        m.queueds!!.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        b.rv.adapter?.notifyItemRangeChanged(it, m.queueds!!.size)
                        if (it > 0) b.rv.adapter?.notifyItemChanged(it - 1)
                    }
                    HANDLE_CHANGED -> find(msg)?.let {
                        if (it == -1) return@let
                        m.queueds!![it] = msg.obj as Queued
                        b.rv.adapter?.notifyItemChanged(it)
                    }
                    HANDLE_RESET ->
                        if (b.rv.adapter == null) b.rv.adapter = ListQud(this@Downloads)
                        else b.rv.adapter?.notifyDataSetChanged()
                }
                updateIfEmpty(m.queueds.isNullOrEmpty()) {
                    loadInterstitial("ca-app-pub-9457309151954418/4215022118")
                    Main.doNotShowInterstitialAgain = true
                }
                b.empty.vis(m.queueds.isNullOrEmpty())
            }

            fun find(msg: Message): Int? =
                if (m.queueds != null) Queued.find(msg.obj as Queued, m.queueds!!) else null
        }

        // Paste Link
        b.linkButton.setOnClickListener {
            if (b.pasteLink.text.toString() == "") return@setOnClickListener
            initService(this, b.pasteLink.text.toString())
            b.pasteLink.setText("")
        }
        if (!night()) color(R.color.CSD).apply {
            b.pasteLink.setTextColor(this)
            b.pasteLink.setHintTextColor(Color.argb(100, red, green, blue))
        }
        b.pasteLink.typeface = fontRegular

        // More
        b.downloadsHelp1.typeface = fontRegular
        b.downloadsHelp2.typeface = fontRegular
        ItemTouchHelper(SwipeToRemove()).attachToRecyclerView(b.rv)
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { initService(this, it) }
        return super.resolveIntent(intent, false)
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO).launch {
            m.queueds = ArrayList(dao.queueds())
            m.queueds!!.sortBy { it.addedAt }
            handler?.obtainMessage(HANDLE_RESET)?.sendToTarget()
        }
    }

    override fun onInitializationComplete(adsInitStatus: InitializationStatus) {
        adBanner = UiTools.adaptiveBanner(this, "ca-app-pub-9457309151954418/4315014912")
        b.root.addView(adBanner, UiTools.adaptiveBannerLp())
        adBanner.loadAd(AdRequest.Builder().build())
        b.rv.layoutParams = (b.rv.layoutParams as ConstraintLayout.LayoutParams)
            .apply { bottomToTop = R.id.adBanner }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)
        Queuer.active.observe(this) { updateControlButton(it) }
        return ret
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.dtControl -> if (!m.queueds.isNullOrEmpty()) {
                if (Queuer.active.value!!) stopService(Intent(c, Queuer::class.java)
                    .apply { action = ForegroundService.ACTION_STOP })
                else initService(this@Downloads)
            }
        }
        return super.onMenuItemClick(item)
    }

    companion object : ActivityCompanion() {
        @MainThread
        fun initService(c: BaseActivity, link: String? = null) {
            if (c.sPreference(Settings.spStorage) == null) {
                c.startActivity(Intent(c, Settings::class.java).apply {
                    putExtra(Settings.EXTRA_GIVE_LINK_BACK, link)
                    putExtra(Settings.EXTRA_IS_GLOBAL, true)
                })
                return; }
            if (Queuer.active.value!!) {
                if (link != null)
                    Queuer.handler?.obtainMessage(Queuer.HANDLE_LINK, link)?.sendToTarget()
                return
            }
            c.startService(Intent(c, Queuer::class.java).apply {
                action = ForegroundService.ACTION_START
                if (link != null) putExtra(Queuer.EXTRA_LINK, link)
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
            val q = m.queueds?.getOrNull(h.layoutPosition) ?: return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    dao.deleteQueued(q)
                } catch (e: Exception) {
                }
                if (m.queueds != null) withContext(Dispatchers.Main) {
                    Queued.find(q, m.queueds)?.let {
                        m.queueds?.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        if (m.queueds == null) return@let
                        b.rv.adapter?.notifyItemRangeChanged(it, m.queueds!!.size - 1)
                        if (it > 0) b.rv.adapter?.notifyItemChanged(it - 1)
                    }
                }
                handler?.obtainMessage(-1)?.sendToTarget()
            }
        }
    }
}
