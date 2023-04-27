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
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.mahdiparastesh.chlm.ChipsLayoutManager
import ir.mahdiparastesh.instatools.data.Followable
import ir.mahdiparastesh.instatools.databinding.MassFollowerBinding
import ir.mahdiparastesh.instatools.list.ListFwb
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Delay
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
                    HANDLE_DETECTED_AS_SPAMMER -> MaterialAlertDialogBuilder(this@MassFollower)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)
        Follower.active.observe(this) { updateControlButton(it) }
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
                MaterialAlertDialogBuilder(this@MassFollower).apply {
                    setTitle(R.string.listClear)
                    setMessage(R.string.listClearSure)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            dao.deleteFollowables()
                            withContext(Dispatchers.Main) { mm.fwb.value = arrayListOf() }
                        }
                    }
                }.show()
            R.id.mfTroubleshoot -> MaterialAlertDialogBuilder(this@MassFollower).apply {
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

    override fun onStateChanged(hasContent: Boolean) {
        super.onStateChanged(hasContent)
        mm.fwb.value = mm.fwb.value
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

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        Delay(500L) { mm.fwb.value = null }
        super.onBackPressed()
    }

    companion object : ActivityCompanion() {
        const val HANDLE_DETECTED_AS_SPAMMER = 6
        const val FOLLOW_LIMIT = 9999 // edit EditText's maxLength whenever you edit this.

        fun initService(
            c: BaseActivity, enq: Follower.ToBeEnqueued? = null, onStart: () -> Unit = {}
        ) {
            onStart()
            actuallyInitService(c, enq)
        }

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
            } catch (_: ActivityNotFoundException) {
            }
        }
    }
}
