package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
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
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MassFollower : BaseActivity() {
    private lateinit var b: MassFollowerBinding
    private lateinit var adBanner: AdView
    val seekMin: Int by lazy { resources.getInteger(R.integer.mfMin) }

    override val menuRes = R.menu.follower_tlb
    override val com: ActivityCompanion get() = Companion

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = MassFollowerBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.massFollower)

        handler = object : Handler(Looper.getMainLooper()) {
            var lastState = true

            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_INSERTED -> (msg.obj as List<Followable>).apply {
                        m.fwb.value!!.addAll(this)
                        val firstPos = (m.fwb.value?.size ?: 1) - 1
                        b.rv.adapter?.notifyItemRangeInserted(firstPos, firstPos + size)
                    }
                    HANDLE_DELETED -> find(msg)?.let {
                        m.fwb.value!!.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        b.rv.adapter?.notifyItemRangeChanged(it, m.fwb.value!!.size)
                    }
                }
                val newState = !m.queueds.isNullOrEmpty()
                if (lastState != newState) {
                    findControl()?.isEnabled = newState
                    lastState = newState
                }
            }

            fun find(msg: Message): Int? = if (m.fwb.value != null)
                Followable.find(msg.obj as Followable, m.fwb.value!!) else null
        }

        // Overflow Menu
        Follower.active.observe(this) {
            b.toolbar.menu.findItem(R.id.mftControl)?.apply {
                setIcon(if (it) R.drawable.pause else R.drawable.play)
                setTitle(if (it) R.string.stop else R.string.start)
            }
        }
        Follower.active.value = Follower.active.value

        // Guide
        arrayOf(b.guideTv1, b.guideTv2, b.guideTv3)
            .forEach { it.typeface = fontRegular }
        b.guideIv.setOnClickListener {
        }

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
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO).launch {
            val data = dao.followables()
            withContext(Dispatchers.Main) { m.fwb.value = ArrayList(data) }
        }
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

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mftControl -> {
                if (Follower.active.value!!) stopService(Intent(c, Follower::class.java)
                    .apply { action = ForegroundService.ACTION_STOP })
                else initService(this)
            }
            R.id.mftClear -> {
            }
        }
        return super.onMenuItemClick(item)
    }

    private fun indicateSeek(updateSb: Boolean = false) {
        b.seekIndicator.text = getString(R.string.mfSeconds, Follower.DELAY / 1000)
        if (updateSb) b.seek.progress = (Follower.properDelay(this).toInt() / 1000) - seekMin
    }

    private fun findControl() = b.toolbar.menu.findItem(R.id.mftControl)

    companion object : ActivityCompanion() {
        const val HANDLE_INSERTED = 0
        const val HANDLE_DELETED = 1

        @MainThread
        fun initService(c: BaseActivity, enq: Follower.ToBeEnqueued? = null) {
            c.startService(Intent(c, Follower::class.java).apply {
                action = ForegroundService.ACTION_START
                if (enq != null) putExtra(Follower.EXTRA_ENQUEUE, enq)
            })
        }
    }
}
