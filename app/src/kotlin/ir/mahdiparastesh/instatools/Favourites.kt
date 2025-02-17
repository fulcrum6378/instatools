package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.view.UserListActivity
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        prepareListing(this)
        load()
    }

    override fun onResume() {
        super.onResume()
        if (notFirstResume) load()
    }

    override fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            m.fav = ArrayList(dao.favourites())
            m.fav?.sortBy { it.user }

            withContext(Dispatchers.Main) {
                b.refresher.isRefreshing = false
                adapt()
                updateCount(m.fav?.size ?: 0)
            }
        }
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
}
