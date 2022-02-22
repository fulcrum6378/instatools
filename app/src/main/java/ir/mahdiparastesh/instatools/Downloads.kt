package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.annotation.MainThread
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.recyclerview.selection.SelectionTracker
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.DownloadsBinding
import ir.mahdiparastesh.instatools.list.ListQud
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.serv.Queuer
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Downloads : BaseActivity() {
    lateinit var b: DownloadsBinding
    override val menuRes: Int? = null
    private lateinit var adBanner: AdView
    var tracker: SelectionTracker<String>? = null
    private var selectivity = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = DownloadsBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.downloads)
        db = Database.build(c, (m.acc?.id ?: -1L).toString()).also { dao = it.dao() }

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
            }

            fun find(msg: Message): Int? =
                if (m.queueds != null) Queued.find(msg.obj as Queued, m.queueds!!) else null
        }

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

        // Ads
        MobileAds.initialize(c) {
            adBanner = UiTools.adaptiveBanner(this, "ca-app-pub-9457309151954418/4315014912")
            b.root.addView(adBanner, UiTools.adaptiveBannerLp())
            adBanner.loadAd(AdRequest.Builder().build())
        }
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
            if (!m.queueds.isNullOrEmpty())
                withContext(Dispatchers.Main) { initService(this@Downloads) }
            handler?.obtainMessage(HANDLE_RESET)?.sendToTarget()
        }
    }

    override fun onDestroy() {
        handler = null
        super.onDestroy()
    }

    companion object {
        const val HANDLE_INSERTED = 0
        const val HANDLE_DELETED = 1
        const val HANDLE_CHANGED = 2
        const val HANDLE_RESET = 3
        var handler: Handler? = null

        @MainThread
        fun initService(c: BaseActivity, link: String? = null) {
            if (c.sPreference(Settings.spStorage) == null) {
                c.startActivity(Intent(c, Settings::class.java).apply {
                    putExtra(Settings.EXTRA_GIVE_LINK_BACK, link)
                    putExtra(Settings.EXTRA_IS_GLOBAL, true)
                })
                return; }
            if (Queuer.active) {
                if (link != null)
                    Queuer.handler?.obtainMessage(Queuer.HANDLE_LINK, link)?.sendToTarget()
                return
            }
            c.startService(Intent(c, Queuer::class.java).apply {
                if (link != null) putExtra(Queuer.EXTRA_LINK, link)
                action = ForegroundService.ACTION_START
            })
        }
    }
}
