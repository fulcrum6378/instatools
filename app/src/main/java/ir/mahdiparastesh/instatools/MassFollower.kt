package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import ir.mahdiparastesh.chlm.ChipsLayoutManager
import ir.mahdiparastesh.instatools.data.Followable
import ir.mahdiparastesh.instatools.databinding.MassFollowerBinding
import ir.mahdiparastesh.instatools.databinding.PayForItBinding
import ir.mahdiparastesh.instatools.list.ListFwb
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.bolden
import ir.mahdiparastesh.instatools.view.UiTools.Companion.inaccurateTime
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
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
    private var controllerBadge: BadgeDrawable? = null

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
                    HANDLE_REWARD_CONSUMED -> countPermissions()
                }
                updateIfEmpty(m.fwb.value.isNullOrEmpty())
                updateShadow()
                estimate()
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
            if (!queued) b.tbShadow.vis(false)
            b.panelShadow.vis(queued)
            b.guide.vis(!queued)
            if (queued) {
                if (b.rv.adapter == null) b.rv.adapter = ListFwb(this)
                else b.rv.adapter?.notifyDataSetChanged()
            }
            updateIfEmpty(m.fwb.value.isNullOrEmpty())
            estimate()
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
            }
        })

        // Bottom Panel
        b.seekTitle.typeface = fontLight
        b.seekIndicator.typeface = fontLight
        if (Follower.active.value != true) Follower.DELAY =
            sp?.getLong(Settings.spFollowerDelay, Settings.defSpFollowerDelay)
                ?: Settings.defSpFollowerDelay
        b.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                Follower.DELAY = ((progress + seekMin) * 1000).toLong()
                indicateSeek()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sp?.edit()?.putLong(Settings.spFollowerDelay, Follower.DELAY)?.apply()
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
        countPermissions()
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
        b.seekIndicator.text = c.inaccurateTime(Follower.DELAY)
        if (updateSb) b.seek.progress = (Follower.properDelay(this).toInt() / 1000) - seekMin
        estimate()
    }

    private fun estimate() {
        b.seekTitle.text = getString(
            R.string.mfSeekTitle,
            c.inaccurateTime(Follower.DELAY * (m.fwb.value?.size ?: 0)),
            (m.fwb.value?.size ?: 0)
        )
    }

    private fun updateShadow() {
        b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun countPermissions() {
        BadgeUtils.detachBadgeDrawable(controllerBadge, b.toolbar, R.id.mftControl)
        BadgeUtils.attachBadgeDrawable(
            BadgeDrawable.create(
                ContextThemeWrapper(c, R.style.Theme_MaterialComponents_DayNight)
            ).apply {
                number = m.acc?.mfrw ?: 0
                backgroundColor = if (!night()) color(R.color.CP) else color(R.color.defCA)
                badgeTextColor = if (!night()) color(R.color.defBG) else color(R.color.CP)
                controllerBadge = this
            }, b.toolbar, R.id.mftControl
        )
    }

    companion object : ActivityCompanion() {
        const val HANDLE_REWARD_CONSUMED = 5
        private val UNLOCK_TIMES = arrayOf(50, 5)
        private var mRewardedAd: RewardedAd? = null
        private var loadingAd = false

        fun initService(
            c: BaseActivity, enq: Follower.ToBeEnqueued? = null, onStart: () -> Unit = {}
        ) {
            if (loadingAd || mRewardedAd != null) return
            if (c.m.acc!!.mfrw > 0) {
                onStart()
                actuallyInitService(c, enq)
                return; }
            val bp = PayForItBinding.inflate(c.layoutInflater)
            bp.root.forEachIndexed { i, v ->
                if (v !is LinearLayout) return@forEachIndexed
                val tvLl = v[1] as LinearLayout
                (tvLl[0] as TextView).typeface = c.fontBold
                (tvLl[1] as TextView).apply {
                    text = c.getString(R.string.mfUnlockTimes, UNLOCK_TIMES[i])
                    typeface = c.fontRegular
                }
            }
            AlertDialog.Builder(c).apply {
                setTitle(R.string.massFollower)
                setMessage(R.string.mfPayForIt)
                setView(bp.root)
            }.show().apply {
                stylise(c)
                if (c.gsp.getBoolean(Settings.spRatedUs, false))
                    bp.root.removeView(bp.rateUs)
                else bp.rateUs.setOnClickListener {
                    bp.loading(true)
                    val reviewManager =
                        if (!BuildConfig.DEBUG) ReviewManagerFactory.create(c)
                        else FakeReviewManager(c)
                    reviewManager.requestReviewFlow().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            this@apply.cancel()
                            reviewManager.launchReviewFlow(c, task.result).addOnCompleteListener {
                                c.rewardAccount(UNLOCK_TIMES[0])
                                c.gsp.edit().putBoolean(Settings.spRatedUs, true).apply()
                            }
                        } else if (BuildConfig.DEBUG) task.exception?.let { throw it }
                        bp.loading(false)
                    }
                }
                bp.watchAnAd.setOnClickListener {
                    bp.loading(true)
                    watchAnAd(c, enq, onStart) {
                        bp.loading(it)
                        if (it) cancel()
                    }
                }
            }
        }

        private fun PayForItBinding.loading(bb: Boolean) {
            root.forEach { if (it is LinearLayout) it.vis(!bb) }
            loading.vis(bb)
            if (bb) loading.playAnimation()
            else loading.pauseAnimation()
        }

        @MainThread
        private fun watchAnAd(
            c: BaseActivity, enq: Follower.ToBeEnqueued? = null, onStart: () -> Unit,
            onResult: (success: Boolean) -> Unit
        ) {
            loadingAd = true
            RewardedAd.load(
                c, "ca-app-pub-9457309151954418/3824726608",
                AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        loadingAd = false
                        mRewardedAd = rewardedAd
                        mRewardedAd?.fullScreenContentCallback = RewardAdCallback(c, onResult)
                        mRewardedAd?.show(c) {
                            c.rewardAccount(it.amount)
                            actuallyInitService(c, enq)
                            onStart()
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        onResult(false)
                        loadingAd = false
                        Toast.makeText(
                            c, c.getString(R.string.failedToLoadAd, adError.message),
                            Toast.LENGTH_LONG
                        ).show()
                        mRewardedAd = null
                    }
                })
        }

        private fun BaseActivity.rewardAccount(times: Int) {
            m.acc!!.mfrw += times
            m.acc!!.saveMe(c)
            if (this is MassFollower) countPermissions()
        }

        @MainThread
        private fun actuallyInitService(c: BaseActivity, enq: Follower.ToBeEnqueued? = null) {
            c.startService(Intent(c, Follower::class.java).apply {
                action = ForegroundService.ACTION_START
                if (enq != null) putExtra(Follower.EXTRA_ENQUEUE, enq)
            })
        }
    }

    class RewardAdCallback(
        private val c: Context,
        private val onResult: (success: Boolean) -> Unit
    ) :
        FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            onResult(true)
        }

        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            onResult(false)
            Toast.makeText(
                c, c.getString(R.string.failedToShowAd, adError.message), Toast.LENGTH_LONG
            ).show()
            mRewardedAd = null
        }

        override fun onAdDismissedFullScreenContent() {
            mRewardedAd = null
        }
    }
}
