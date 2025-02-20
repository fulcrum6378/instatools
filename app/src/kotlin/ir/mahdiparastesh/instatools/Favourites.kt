package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.view.CounterActivity
import ir.mahdiparastesh.instatools.view.Lister
import ir.mahdiparastesh.instatools.view.UiTools.vis
import ir.mahdiparastesh.instatools.view.UiTools.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Favourites : CounterActivity(), Lister {
    private lateinit var b: FavouritesBinding

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion
    override val root: ConstraintLayout? get() = if (bInitialised) b.root else null
    override val bInitialised: Boolean get() = ::b.isInitialized
    override var shouldShowJumper = MutableLiveData(false)
    override var anJumper: ObjectAnimator? = null
    override val heightPixels: Int by lazy { dm.heightPixels }

    companion object : ActivityCompanion()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FavouritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.favouritesTb)

        if (!Main.guest)
            prepareListing(this)
        else {
            jumper?.vis(false)
            rv?.vis(false)
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (notFirstResume) load()
    }

    fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            m.fav = ArrayList(dao.favourites())
            m.fav?.sortBy { it.user }

            withContext(Dispatchers.Main) {
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

    override fun updateShadow() {
        if (::b.isInitialized) b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
    }
}
