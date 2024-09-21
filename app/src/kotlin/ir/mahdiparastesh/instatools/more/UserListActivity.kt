package ir.mahdiparastesh.instatools.more

import android.animation.ObjectAnimator
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.view.CounterActivity
import ir.mahdiparastesh.instatools.view.DataLoader
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish

abstract class UserListActivity : CounterActivity(), DataLoader {

    abstract val bRefresher: SwipeRefreshLayout
    abstract val bTbShadow: View
    override val heightPixels: Int by lazy { dm.heightPixels }
    override var shouldShowJumper = MutableLiveData(false)
    override var anJumper: ObjectAnimator? = null

    companion object {
        const val HANDLE_LOADED = 0
    }

    override fun prepareListing(c: BaseActivity) {
        super.prepareListing(c)
        if (!Main.guest) bRefresher.setOnRefreshListener { load() }
        else {
            bRefresher.isEnabled = false
            jumper()?.vis(false)
            rv()?.vis(false)
        }
    }

    abstract fun load()

    override fun updateShadow() {
        if (bInitialised) bTbShadow.vish(rv()!!.computeVerticalScrollOffset() > 0)
    }

    override fun onRecyclerViewScrolled() {
        super.onRecyclerViewScrolled()
        updateShadow()
    }
}
