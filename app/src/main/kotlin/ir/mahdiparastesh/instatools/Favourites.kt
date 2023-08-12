package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UserListActivity
import ir.mahdiparastesh.instatools.view.UiTools.vis

class Favourites : UserListActivity() {
    private lateinit var b: FavouritesBinding

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion
    override val bRefresher: SwipeRefreshLayout get() = b.refresher
    override val bRv: RecyclerView get() = b.rv
    override val bTbShadow: View get() = b.tbShadow
    override val bJumper: ImageView get() = b.jumper

    companion object : ActivityCompanion()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FavouritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.favourites)

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LOADED -> {
                        b.refresher.isRefreshing = false
                        adapt()
                        updateCount(m.fav?.size ?: 0)
                    }
                }
            }
        }

        prepare()
        load()
    }

    override fun onResume() {
        super.onResume()
        if (notFirstResume) load()
    }

    override fun load() {
        FavLoader(this).start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (m.fav!!.isNotEmpty()) {
            if (b.rv.adapter == null) b.rv.adapter = ListFav(this)
            else b.rv.adapter?.notifyDataSetChanged()
        } else {
            b.rv.vis(false)
            b.empty.vis(true)
        }
    }

    class FavLoader(private val c: BaseActivity) : Thread() {
        override fun run() {
            c.m.fav = ArrayList(c.dao.favourites())
            c.m.fav?.sortBy { it.user }
            handler?.obtainMessage(HANDLE_LOADED)?.sendToTarget()
        }
    }
}
