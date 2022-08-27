package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.view.Menu
import android.view.MenuItem
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.chlm.ChipsLayoutManager
import ir.mahdiparastesh.instatools.data.Followable
import ir.mahdiparastesh.instatools.databinding.MassFollowerBinding
import ir.mahdiparastesh.instatools.list.ListFwb
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.serv.Follower
import ir.mahdiparastesh.instatools.view.UiTools.inaccurateTime
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MassFollower : ServiceOwnerActivity() {
    private lateinit var b: MassFollowerBinding
    val seekMin: Int by lazy { resources.getInteger(R.integer.mfMin) }
    val mm: MyModel by viewModels()
    /*private lateinit var adBanner: AdView
    private var controllerBadge: BadgeDrawable? = null*/

    override val menuRes = R.menu.follower_tlb
    override val com: ActivityCompanion get() = Companion
    override val controllerId = R.id.mftControl

    class MyModel : ViewModel() {
        var fwb = MutableLiveData<ArrayList<Followable>?>(null)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = MassFollowerBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.massFollower)

        handler = object : Handler(Looper.getMainLooper()) {
            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_INSERTED -> (msg.obj as List<Followable>).apply {
                        if (mm.fwb.value == null) mm.fwb.value = ArrayList(this)
                        else {
                            val wasEmpty = mm.fwb.value!!.isEmpty()
                            mm.fwb.value!!.addAll(this)
                            if (wasEmpty) mm.fwb.value = mm.fwb.value
                        }
                        val firstPos = (mm.fwb.value?.size ?: 1) - 1
                        b.rv.adapter?.notifyItemRangeInserted(firstPos, firstPos + size)
                    }
                    HANDLE_DELETED -> find(msg)?.let {
                        mm.fwb.value!!.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        b.rv.adapter?.notifyItemRangeChanged(it, mm.fwb.value!!.size)
                    }
                    // HANDLE_REWARD_CONSUMED -> countPermissions()
                    HANDLE_DETECTED_AS_SPAMMER -> AlertDialog.Builder(this@MassFollower)
                        .apply {
                            setTitle(R.string.massFollower)
                            setMessage(R.string.mfDetectedSpam)
                            setNeutralButton(R.string.ok, null)
                        }.show()
                }
                updateIfEmpty(mm.fwb.value.isNullOrEmpty())
                updateShadow()
                estimate()
            }

            fun find(msg: Message): Int? = if (mm.fwb.value != null)
                Followable.find(msg.obj as Followable, mm.fwb.value!!) else null
        }

        // Listing
        b.rv.layoutManager = ChipsLayoutManager.newBuilder(this).build()
        mm.fwb.observe(this) {
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
            updateIfEmpty(mm.fwb.value.isNullOrEmpty())
            estimate()
        }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
            }
        })

        // Bottom Panel
        if (Follower.active.value != true) Follower.DELAY =
            sp?.getLong(Settings.spFollowerDelay, Settings.defSpFollowerDelay)
                ?: Settings.defSpFollowerDelay
        b.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                Follower.DELAY = (progress + seekMin) * 1000L
                indicateSeek()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sp?.edit { putLong(Settings.spFollowerDelay, Follower.DELAY) }
            }
        })

        if (mm.fwb.value == null) load()
    }

    override fun onResume() {
        super.onResume()
        if (notFirstResume) load()
        indicateSeek(true)
    }

    /*override fun onInitializationComplete(adsInitStatus: InitializationStatus) {
        super.onInitializationComplete(adsInitStatus)
        if (!adsInitStatus.isReady()) return
        adBanner = UiTools.adaptiveBanner(this, R.string.bnrBtmMassFollower)
        b.root.addView(adBanner, UiTools.adaptiveBannerLp())
        adBanner.loadAd(AdRequest.Builder().build())
        b.panel.layoutParams = (b.panel.layoutParams as ConstraintLayout.LayoutParams)
            .apply { bottomToTop = R.id.adBanner }
        b.guide.layoutParams = (b.guide.layoutParams as ConstraintLayout.LayoutParams)
            .apply { bottomToTop = R.id.adBanner }
    }*/

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)
        Follower.active.observe(this) { updateControlButton(it) }
        // countPermissions()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            b.toolbar.menu.findItem(R.id.mfTroubleshoot).isVisible = false
        return ret
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mftControl -> if (!mm.fwb.value.isNullOrEmpty()) {
                if (Follower.active.value!!) stopService(Intent(c, Follower::class.java)
                    .apply { action = ForegroundService.ACTION_STOP })
                else initService(this)
            }
            R.id.mfExport -> if (!mm.fwb.value.isNullOrEmpty())
                startActivity(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.mfExportSubject, mm.fwb.value?.size ?: 0)
                    )
                    putExtra(Intent.EXTRA_TEXT, mm.fwb.value?.joinToString("\n") { it.user })
                })
            R.id.mftClear -> if (!mm.fwb.value.isNullOrEmpty())
                CoroutineScope(Dispatchers.IO).launch {
                    dao.deleteFollowables()
                    withContext(Dispatchers.Main) { mm.fwb.value = arrayListOf() }
                }
            R.id.mfTroubleshoot -> AlertDialog.Builder(this@MassFollower).apply {
                setTitle(R.string.mfTroubleshoot)
                // mfTroubleshootMsg: Make sure the following conditions are applied to this app:
                val arr = resources.getStringArray(R.array.mfTroubleshoot).toMutableList()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) arr.removeAt(1)
                setItems(arr.toTypedArray()) { _, i ->
                    when (i) {
                        0 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !(getSystemService(Context.POWER_SERVICE) as PowerManager)
                                .isIgnoringBatteryOptimizations(packageName)
                        ) appSettings(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        // https://developer.android.com/training/monitoring-device-state/doze-standby.html
                        1 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            appSettings(
                                android.provider.Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS
                            ) {
                                data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                            }
                    }
                }
            }.show()
        }
        return super.onMenuItemClick(item)
    }

    private fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            val data = dao.followables()
            withContext(Dispatchers.Main) { mm.fwb.value = ArrayList(data) }
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
            c.inaccurateTime(Follower.DELAY * (mm.fwb.value?.size ?: 0)),
            (mm.fwb.value?.size ?: 0)
        )
    }

    private fun updateShadow() {
        b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        mm.fwb.value = null
        super.onBackPressed()
    }

    /*@SuppressLint("UnsafeOptInUsageError")
    fun countPermissions() {
        BadgeUtils.detachBadgeDrawable(controllerBadge, b.toolbar, R.id.mftControl)
        BadgeUtils.attachBadgeDrawable(
            BadgeDrawable.create(ContextThemeWrapper(c, UiTools.materialTheme)).apply {
                number = m.acc?.mfrw ?: 0
                backgroundColor = if (!night()) color(R.color.CP) else color(R.color.defCA)
                badgeTextColor = if (!night()) color(R.color.defBG) else color(R.color.CP)
                controllerBadge = this
                maxCharacterCount = 2
            }, b.toolbar, R.id.mftControl
        )
    }*/

    companion object : ActivityCompanion() {
        const val HANDLE_REWARD_CONSUMED = 5
        const val HANDLE_DETECTED_AS_SPAMMER = 6
        const val FOLLOW_LIMIT = 9999 // edit EditText's maxLength whenever you edit this.
        /*const val RATE_US_UNINTENTIONALLY_UNLOCK_TIMES = 10
        private val UNLOCK_TIMES = arrayOf(50, 5)
        private var mRewardedAd: RewardedAd? = null
        private var loadingAd = false*/

        fun initService(
            c: BaseActivity, enq: Follower.ToBeEnqueued? = null, onStart: () -> Unit = {}
        ) {
            /*if (loadingAd || mRewardedAd != null) return
            if (c.m.acc!!.mfrw > 0) {
                onStart()
                actuallyInitService(c, enq)
                return; }
            val bp = PayForItBinding.inflate(c.layoutInflater)
            bp.root.forEachIndexed { i, v ->
                if (v !is LinearLayout) return@forEachIndexed
                ((v[1] as LinearLayout)[1] as AppCompatTextView)
                    .text = c.getString(R.string.mfUnlockTimes, UNLOCK_TIMES[i])
            }
            AlertDialog.Builder(c).apply {
                setTitle(R.string.massFollower)
                setMessage(R.string.mfPayForIt)
                setView(bp.root)
            }.show().apply {
                if (UiTools.hasReviewedApp(c))
                    bp.root.removeView(bp.rateUs)
                else bp.rateUs.setOnClickListener {
                    bp.loading(true)
                    UiTools.reviewApp(
                        c, UNLOCK_TIMES[0], { this@apply.cancel() }, { bp.loading(false) }
                    )
                }
                bp.watchAnAd.setOnClickListener {
                    bp.loading(true)
                    watchAnAd(c, enq, onStart) {
                        bp.loading(it)
                        if (it) cancel()
                    }
                }
            }*/
            onStart()
            actuallyInitService(c, enq)
        }

        /*private fun PayForItBinding.loading(bb: Boolean) {
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
                c, c.getString(R.string.rewardMfwStarter),
                AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        loadingAd = false
                        mRewardedAd = rewardedAd
                        mRewardedAd?.fullScreenContentCallback = RewardAdCallback(c, onResult)
                        mRewardedAd?.show(c) {
                            c.rewardAccountForFollower(it.amount)
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

        fun BaseActivity.rewardAccountForFollower(times: Int) {
            m.acc?.apply {
                mfrw += times
                saveMe(c)
            }
            if (this is MassFollower) countPermissions()
        }*/

        @MainThread
        private fun actuallyInitService(c: BaseActivity, enq: Follower.ToBeEnqueued? = null) {
            c.startService(Intent(c, Follower::class.java).apply {
                action = ForegroundService.ACTION_START
                if (enq != null) putExtra(Follower.EXTRA_ENQUEUE, enq)
            })
        }

        fun BaseActivity.appSettings(action: String, onIntent: (Intent.() -> Unit)? = null) {
            try {
                startActivity(Intent(action).apply { onIntent?.let { it() } })
            } catch (e: ActivityNotFoundException) {
            }
        }
    }

    /*class RewardAdCallback(
        private val c: Context, private val onResult: (success: Boolean) -> Unit
    ) : FullScreenContentCallback() {
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
    }*/
}
