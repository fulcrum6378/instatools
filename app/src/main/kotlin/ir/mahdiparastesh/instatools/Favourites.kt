package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
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
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val bInitialised: Boolean get() = ::b.isInitialized
    override val bRefresher: SwipeRefreshLayout get() = b.refresher
    override val bTbShadow: View get() = b.tbShadow

    companion object : ActivityCompanion()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FavouritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.favouritesTb)

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

        prepareListing(this)
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
