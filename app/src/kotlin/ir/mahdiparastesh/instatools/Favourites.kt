package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.view.CounterActivity
import ir.mahdiparastesh.instatools.view.Lister
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Favourites : CounterActivity(), Lister {
    private lateinit var b: FavouritesBinding

    override val com: ActivityCompanion get() = Companion
    override val root: ConstraintLayout? get() = if (isBInitialised()) b.root else null
    override val menuRes: Int? = null
    override val tbShadow get() = b.tbShadow
    override var shouldShowJumper = MutableLiveData(false)
    override var anJumper: ObjectAnimator? = null
    override val expandable = null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = m.fav != null
    override fun isModelEmpty(): Boolean = m.fav?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListFav(this)
    override fun screenHeight(): Int = dm.heightPixels

    companion object : ActivityCompanion()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FavouritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.favouritesTb)
        prepareListing(this)
    }

    override fun onResume() {
        super.onResume()
        if (notFirstResume) load()
    }

    override fun load(reset: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            m.fav = ArrayList(dao.favourites())
            m.fav?.sortBy { it.user }
            withContext(Dispatchers.Main) { onLoaded() }
        }
    }

    override fun onLoaded() {
        super.onLoaded()
        updateCount(m.fav?.size ?: 0)
    }
}
