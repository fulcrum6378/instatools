package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.SeekBar
import androidx.annotation.MainThread
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.initialization.InitializationStatus
import ir.mahdiparastesh.chlm.ChipsLayoutManager
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

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = MassFollowerBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.massFollower)

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                }
            }
        }

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

        // Control Panel
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
            withContext(Dispatchers.Main) {
                m.fwb.value = ArrayList(data)
                //if (!m.fwb.value.isNullOrEmpty()) initService(this@MassFollower)
            }
        }
        indicateSeek(true)
    }

    override fun onInitializationComplete(adsInitStatus: InitializationStatus) {
        adBanner = UiTools.adaptiveBanner(this, "ca-app-pub-9457309151954418/5087388141")
        b.root.addView(adBanner, UiTools.adaptiveBannerLp())
        adBanner.loadAd(AdRequest.Builder().build())
    }

    private fun indicateSeek(updateSb: Boolean = false) {
        b.seekIndicator.text = getString(R.string.mfSeconds, Follower.DELAY / 1000)
        if (updateSb) b.seek.progress = (Follower.properDelay(this).toInt() / 1000) - seekMin
    }

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
