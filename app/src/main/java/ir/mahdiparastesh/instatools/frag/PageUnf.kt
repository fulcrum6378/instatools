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
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlin.math.roundToInt

class PageUnf(c: Main) : BasePage(c), ViewStub.OnInflateListener {
    lateinit var b: PageUnfBinding
    private lateinit var bu: UnfPreloadBinding
    override lateinit var inflater: LayoutInflater
    override var handler: Handler? = null

    companion object {
        var theHandler: Handler? = null
    }

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        inflater = c.themeInflater(BaseActivity.Theme.PRIMARY)
        b = PageUnfBinding.inflate(
            c.themeInflater(BaseActivity.Theme.PRIMARY, inf), parent, false
        )
        if (Main.guest) {
            guestMode(b.root, BaseActivity.Theme.PRIMARY); return b.root; }

        theHandler = object : Handler(Looper.getMainLooper()) {
            @SuppressLint("NotifyDataSetChanged")
            @Suppress("UNCHECKED_CAST")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    Action.LOADED.ordinal -> (msg.obj as List<Unfollower>).apply {
                        onLoad()
                        c.m.unfollowers = ArrayList(this)
                        c.m.unfollowers!!.sortBy { it.followedBy }
                        if (isNullOrEmpty()) b.preloadStub.inflate() else adapt()
                    }
                    Action.ANALYSED.ordinal -> {
                        ((100f / msg.arg2.toFloat()) * (msg.arg1 + 1f)).roundToInt()

                        /*Unfollower.find(msg.obj as Unfollower, c.m.unfollowers)?.let {
                            c.m.unfollowers!!.add(msg.obj as Unfollower)
                            b.rv.adapter?.notifyItemInserted(c.m.unfollowers!!.size - 1)
                        }*/
                    }
                    /*Unfollower.find(msg.obj as Unfollower, c.m.unfollowers!!)?.let {
                            b.rv.adapter?.notifyItemInserted(it)
                            if (it > 0) b.rv.adapter?.notifyItemRangeChanged(0, it)
                            if (it < c.m.unfollowers!!.size - 1)
                                b.rv.adapter?.notifyItemRangeChanged(
                                    it + 1, c.m.unfollowers!!.size - 1
                                )
                        }*/
                    Action.COMPLETED.ordinal -> {
                        c.m.unfollowers!!.sortBy { it.followedBy }
                        b.rv.adapter?.notifyDataSetChanged()
                        fetching = false
                        b.refresher.isRefreshing = false
                    }
                    Action.ABORTED.ordinal -> {
                        fetching = false
                        b.refresher.isRefreshing = false
                        Snackbar.make(b.root, R.string.unfFailed, Snackbar.LENGTH_SHORT).show()
                    }
                    Action.CANCELLED.ordinal -> {
                        fetching = false
                        b.refresher.isRefreshing = false
                    }
                    Action.COULD_NOT.ordinal ->
                        Snackbar.make(b.root, R.string.unfCouldNot, Snackbar.LENGTH_SHORT).show()
                    Api.HANDLE_ERROR -> {
                        fetching = false
                        b.refresher.isRefreshing = false
                        Snackbar.make(
                            b.root, c.getString(
                                R.string.unknownError,
                                (msg.obj as NetworkResponse?)?.statusCode.toString()
                            ), Snackbar.LENGTH_LONG
                        ).show()
                    }
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
        if (c.m.unfollowers != null) adapt()
        else Thread {
            handler?.obtainMessage(Action.LOADED.ordinal, c.dao.unfollowers())?.sendToTarget()
        }.start()

        return b.root
    }

    override fun onLoad() {
        if (b.root.contains(b.loading)) {
            b.loading.animation?.cancel()
            b.root.removeView(b.loading)
        }
    }

    override fun onInflate(stub: ViewStub, v: View) {
        bu = UnfPreloadBinding.bind(v)
        bu.desc.typeface = c.fontRegular
        bu.hurry.typeface = c.fontBold
        bu.start.setOnClickListener { fetch() }
        bu.hurry.setOnCheckedChangeListener { _, bb -> Inquisitor.hurry = bb }
    }

    private fun fetch() {
        if (fetching) return
        fetching = true
        c.m.unfollowers = arrayListOf()
        c.dao.deleteUnfollowers()
        adapt()
        c.startService(Intent(c, Inquisitor::class.java))
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (b.rv.adapter == null) b.rv.adapter = ListUnf(c, this)
        else b.rv.adapter?.notifyDataSetChanged()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        else -> false
    }

    override fun updateShadow() {
        c.b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
    }

    override fun updateJumper() {
    }

    enum class Action { LOADED, ANALYSED, COMPLETED, ABORTED, COULD_NOT, CANCELLED }
}
