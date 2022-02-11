package ir.mahdiparastesh.instatools.frag

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.*
import androidx.core.view.contains
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.NetworkResponse
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Unfollower
import ir.mahdiparastesh.instatools.databinding.PageUnfBinding
import ir.mahdiparastesh.instatools.databinding.UnfPreloadBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.list.ListUnf
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.serv.Inquisitor
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import java.text.DecimalFormat

class PageUnf(c: Main) : BasePage(c), ViewStub.OnInflateListener {
    lateinit var b: PageUnfBinding
    private lateinit var bu: UnfPreloadBinding
    override lateinit var inflater: LayoutInflater
    override var handler: Handler? = null

    companion object {
        const val HANDLE_LOADED = 2
        const val HANDLE_ANALYSED = 3
        const val HANDLE_COMPLETED = 4
        const val HANDLE_COULD_NOT = 5
        var theHandler: Handler? = null
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        inflater = c.themeInflater(BaseActivity.Theme.PRIMARY)
        b = PageUnfBinding.inflate(
            c.themeInflater(BaseActivity.Theme.PRIMARY, inf), parent, false
        )
        if (Main.guest) {
            b.refresher.isEnabled = false
            guestMode(b.root, BaseActivity.Theme.PRIMARY); return b.root; }

        theHandler = object : Handler(Looper.getMainLooper()) {
            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LOADED -> (msg.obj as List<Unfollower>).apply {
                        onLoaded()
                        c.m.unfollowers = ArrayList(this)
                        c.m.unfollowers!!.sortBy { it.followedBy }
                        if (isNullOrEmpty()) preload() else adapt()
                    }
                    Api.HANDLE_ERROR -> { // fetching following error
                        fetching = false
                        b.refresher.isRefreshing = false
                        Snackbar.make(
                            b.root, c.getString(
                                R.string.unknownError,
                                (msg.obj as NetworkResponse?)?.statusCode.toString()
                            ), Snackbar.LENGTH_LONG
                        ).show()
                    }
                    HANDLE_FETCHED -> {
                        calcSum = msg.obj as Int
                        b.refresher.isRefreshing = false
                    }
                    HANDLE_ABORTED -> {
                        analysing(false)
                        fetching = false
                        b.refresher.isRefreshing = false
                        Snackbar.make(b.root, R.string.unfFailed, Snackbar.LENGTH_SHORT).show()
                        preload()
                    }
                    HANDLE_ANALYSED -> {
                        calcItem = (msg.obj as Int) + 1
                        calculate()
                    }
                    HANDLE_COMPLETED -> {
                        analysing(false)
                        fetching = false
                        c.m.unfollowers = ArrayList(msg.obj as List<Unfollower>)
                        c.m.unfollowers!!.sortBy { it.followedBy }
                        adapt()
                        onLoaded()
                    }
                    HANDLE_COULD_NOT ->
                        Snackbar.make(b.root, R.string.unfCouldNot, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        handler = theHandler

        b.preloadStub.setOnInflateListener(this)
        b.refresher.setOnRefreshListener { fetch() }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateShadow()
                updateJumper()
            }
        })

        when {
            Inquisitor.active -> preload()
            c.m.unfollowers != null -> adapt()
            else -> Thread {
                handler?.obtainMessage(HANDLE_LOADED, c.dao.unfollowers())?.sendToTarget()
            }.start()
        }
        return b.root
    }

    override fun onFailed(message: String) {
    }

    override fun onLoaded() {
        b.refresher.isRefreshing = false
        if (b.root.contains(b.loading)) {
            b.loading.animation?.cancel()
            b.root.removeView(b.loading)
        }
        if (::bu.isInitialized) bu.root.vis(false)
    }

    private fun fetch() {
        if (fetching) return
        fetching = true
        preload()
        c.startService(Intent(c, Inquisitor::class.java))
        analysing(true)
    }

    private fun preload() {
        b.rv.adapter = null
        if (!::bu.isInitialized) b.preloadStub.inflate()
        else bu.root.vis()
    }

    private fun analysing(bb: Boolean) {
        bu.calc.vis(bb)
        bu.working.vis(bb)
        if (bb) bu.working.playAnimation()
        else bu.working.pauseAnimation()
        bu.start.vish(!bb)
    }

    override fun onInflate(stub: ViewStub, v: View) {
        bu = UnfPreloadBinding.bind(v)
        bu.calc.typeface = c.fontBold
        bu.desc.typeface = c.fontRegular
        bu.hurry.typeface = c.fontBold

        bu.start.setOnClickListener {
            if (!fetching) fetch()
        }
        bu.hurry.setOnCheckedChangeListener { _, bb ->
            Inquisitor.hurry = bb
            calculate()
        }
    }

    private var calcItem = 0
    private var calcSum = 0
    private fun calculate() {
        if (calcSum <= 0) return
        val seconds = ((((if (!Inquisitor.hurry) Inquisitor.DELAY else Inquisitor.DELAY_HURRY))
            .toFloat() / 1000f) * (calcSum.toFloat() - calcItem.toFloat())).toInt()
        bu.calc.text = c.getString(
            if (seconds >= 60) R.string.unfCalcMin else R.string.unfCalcSec, calcItem, calcSum,
            DecimalFormat("#.##").format((100f / calcSum.toFloat()) * calcItem.toFloat()),
            if (seconds >= 60) seconds / 60 else seconds
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        /*val flwCount = c.sp?.getInt(Settings.spFollowingCount, -2)
        b.stat.vis(flwCount != -2)
        b.stat.text = if (flwCount != -2) c.getString(
            R.string.unfStat, flwCount, c.m.unfollowers?.size ?: 0
        ) else ""*/
        if (b.rv.adapter == null) b.rv.adapter = ListUnf(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
        // TODO: WHAT IF ALL FOLLOWING, FOLLOW BACK!?!
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        else -> false
    }

    override fun updateShadow() {
        c.b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
    }

    override fun updateJumper() {
    }
}
