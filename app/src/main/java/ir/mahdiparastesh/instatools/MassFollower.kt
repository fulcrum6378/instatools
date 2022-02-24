package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.annotation.MainThread
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import ir.mahdiparastesh.chlm.ChipsLayoutManager
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.databinding.MassFollowerBinding
import ir.mahdiparastesh.instatools.list.ListFwb
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MassFollower : BaseActivity() {
    private lateinit var b: MassFollowerBinding
    override val menuRes: Int? = null
    private lateinit var adBanner: AdView

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = MassFollowerBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.massFollower)
        db = Database.build(c, (m.acc?.id ?: -1L).toString()).also { dao = it.dao() }

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
            b.guide.vis(!queued)
            if (queued) {
                if (b.rv.adapter == null) b.rv.adapter = ListFwb(this@MassFollower)
                else b.rv.adapter?.notifyDataSetChanged()
            }
        }

        // Ads
        MobileAds.initialize(c) {
            adBanner = UiTools.adaptiveBanner(this, "ca-app-pub-9457309151954418/5087388141")
            b.root.addView(adBanner, UiTools.adaptiveBannerLp())
            adBanner.loadAd(AdRequest.Builder().build())
        }
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
    }

    override fun onDestroy() {
        handler = null
        super.onDestroy()
    }

    companion object {
        const val HANDLE_INSERTED = 0
        const val HANDLE_DELETED = 1
        var handler: Handler? = null

        @MainThread
        fun initService(c: BaseActivity, enq: Follower.ToBeEnqueued? = null) {
            c.startService(Intent(c, Follower::class.java).apply {
                action = ForegroundService.ACTION_START
                if (enq != null) putExtra(Follower.EXTRA_ENQUEUE, enq)
            })
        }
    }
}
