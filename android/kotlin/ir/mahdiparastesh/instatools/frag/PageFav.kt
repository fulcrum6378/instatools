package ir.mahdiparastesh.instatools.frag

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.databinding.PageFavBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BasePageMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PageFav : BasePageMain(BaseActivity.Theme.PRIMARY) {
    private lateinit var b: PageFavBinding

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val emptyIcon: Int = R.drawable.favourite_on
    override val expandable = null
    override val selectiveMenuRes: Int? = null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun shouldLoadOnPrepare(): Boolean = false
    override fun isModelLoaded(): Boolean = c.m.fav != null
    override fun isModelEmpty(): Boolean = c.m.fav?.isEmpty() == true
    override fun createAdapter(): RecyclerView.Adapter<*> = ListFav(c, this)
    override fun screenHeight(): Int = c.dm.heightPixels

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageFavBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun load(reset: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            c.m.fav = ArrayList(c.dao.favourites())
            c.m.fav?.sortBy { it.user }
            withContext(Dispatchers.Main) { onLoaded() }
        }
    }
}
