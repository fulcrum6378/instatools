package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.initialization.InitializationStatus
import ir.mahdiparastesh.chlm.ChipsLayoutManager
import ir.mahdiparastesh.instatools.data.Followable
import ir.mahdiparastesh.instatools.databinding.MassFollowerBinding
import ir.mahdiparastesh.instatools.list.ListFwb
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.bolden
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MassFollower : ServiceOwnerActivity() {
    private lateinit var b: MassFollowerBinding
    private lateinit var adBanner: AdView
    val seekMin: Int by lazy { resources.getInteger(R.integer.mfMin) }

    override val menuRes = R.menu.follower_tlb
    override val com: ActivityCompanion get() = Companion
    override val controllerId = R.id.mftControl

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = MassFollowerBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.massFollower)

        handler = object : Handler(Looper.getMainLooper()) {
            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_INSERTED -> (msg.obj as List<Followable>).apply {
                        if (m.fwb.value == null) m.fwb.value = ArrayList(this)
                        else {
                            val wasEmpty = m.fwb.value!!.isEmpty()
                            m.fwb.value!!.addAll(this)
                            if (wasEmpty) m.fwb.value = m.fwb.value
                        }
                        val firstPos = (m.fwb.value?.size ?: 1) - 1
                        b.rv.adapter?.notifyItemRangeInserted(firstPos, firstPos + size)
                    }
                    HANDLE_DELETED -> find(msg)?.let {
                        m.fwb.value!!.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        b.rv.adapter?.notifyItemRangeChanged(it, m.fwb.value!!.size)
                    }
                }
                updateIfEmpty(m.fwb.value.isNullOrEmpty())
            }

            fun find(msg: Message): Int? = if (m.fwb.value != null)
                Followable.find(msg.obj as Followable, m.fwb.value!!) else null
        }

        // Guide
        arrayOf(b.guideTv1, b.guideTv2).forEach { it.typeface = fontRegular }
        b.guideTv3.bolden(this)

        // Listing
        b.rv.layoutManager = ChipsLayoutManager.newBuilder(this).build()
        m.fwb.observe(this) {
            val queued = !it.isNullOrEmpty()
            b.rv.vis(queued)
            b.panel.vis(queued)
            b.tbShadow.vis(queued)
            b.panelShadow.vis(queued)
            b.guide.vis(!queued)
            if (queued) {
                if (b.rv.adapter == null) b.rv.adapter = ListFwb(this)
                else b.rv.adapter?.notifyDataSetChanged()
            }
            updateIfEmpty(m.fwb.value.isNullOrEmpty())
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
            }
        })

        // Bottom Panel
        b.seekTitle.typeface = fontLight
        b.seekIndicator.typeface = fontLight
        b.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                Follower.DELAY = ((progress + seekMin) * 1000).toLong()
                indicateSeek()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sp?.edit()?.putLong(Settings.spFollowerDelay, Follower.DELAY)?.commit()
            }
        })

        if (m.fwb.value == null) load()
    }

    override fun onResume() {
        super.onResume()
        if (notFirstResume) load()
        indicateSeek(true)
    }

    override fun onInitializationComplete(adsInitStatus: InitializationStatus) {
        adBanner = UiTools.adaptiveBanner(this, "ca-app-pub-9457309151954418/5087388141")
        b.root.addView(adBanner, UiTools.adaptiveBannerLp())
        adBanner.loadAd(AdRequest.Builder().build())
        b.panel.layoutParams = (b.panel.layoutParams as ConstraintLayout.LayoutParams)
            .apply { bottomToTop = R.id.adBanner }
        b.guide.layoutParams = (b.guide.layoutParams as ConstraintLayout.LayoutParams)
            .apply { bottomToTop = R.id.adBanner }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)
        Follower.active.observe(this) { updateControlButton(it) }
        return ret
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mftControl -> if (!m.fwb.value.isNullOrEmpty()) {
                if (Follower.active.value!!) stopService(Intent(c, Follower::class.java)
                    .apply { action = ForegroundService.ACTION_STOP })
                else initService(this)
            }
            R.id.mftClear -> if (!m.fwb.value.isNullOrEmpty())
                CoroutineScope(Dispatchers.IO).launch {
                    dao.deleteFollowables()
                    withContext(Dispatchers.Main) { m.fwb.value = arrayListOf() }
                }
        }
        return super.onMenuItemClick(item)
    }

    private fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            val data = dao.followables()
            withContext(Dispatchers.Main) { m.fwb.value = ArrayList(data) }
        }
    }

    private fun indicateSeek(updateSb: Boolean = false) {
        b.seekIndicator.text = getString(R.string.mfSeconds, Follower.DELAY / 1000)
        if (updateSb) b.seek.progress = (Follower.properDelay(this).toInt() / 1000) - seekMin
    }

    companion object : ActivityCompanion() {
        @MainThread
        fun initService(c: BaseActivity, enq: Follower.ToBeEnqueued? = null) {
            c.startService(Intent(c, Follower::class.java).apply {
                action = ForegroundService.ACTION_START
                if (enq != null) putExtra(Follower.EXTRA_ENQUEUE, enq)
            })
        }
    }
}
