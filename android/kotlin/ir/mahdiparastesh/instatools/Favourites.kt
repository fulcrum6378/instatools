package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.badge.BadgeDrawable
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.view.Counter
import ir.mahdiparastesh.instatools.view.Lister
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Favourites : BaseActivity(), Lister, Counter {
    private lateinit var b: FavouritesBinding

    override val com: ActivityCompanion get() = Companion
    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val menuRes: Int? = null
    override val tbShadow get() = b.tbShadow
    override var shouldShowJumper: Boolean = false
    override var anJumper: ObjectAnimator? = null
    override val expandable = null
    override var countBadge: BadgeDrawable? = null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun shouldLoadOnPrepare(): Boolean = false
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
        load()
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
        updateCount(this, m.fav?.size ?: 0)
    }
}
