package ir.mahdiparastesh.instatools.frag

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.databinding.PageFavBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BasePageMain

class PageFav : BasePageMain(BaseActivity.Theme.PRIMARY) {
    private lateinit var b: PageFavBinding

    override val root: ConstraintLayout? get() = b.root
    override val rv: RecyclerView? get() = b.rv
    override val empty: View? get() = b.empty
    override val jumper: ImageView? get() = b.jumper
    override val expandable = null
    override val selectiveMenuRes: Int? = null

    override fun isBInitialised(): Boolean = ::b.isInitialized
    override fun isModelLoaded(): Boolean = c.c.fav.value != null
    override fun isModelEmpty(): Boolean = c.vm.favourites.isEmpty()
    override fun createAdapter(): RecyclerView.Adapter<*> = ListFav(c, this)

    override fun onCreateView(inf: LayoutInflater, parent: ViewGroup?, state: Bundle?): View =
        PageFavBinding.inflate(inflater, parent, false).let { b = it; it.root }

    override fun onResume() {
        super.onResume()
        onLoaded()
    }

    override fun load(reset: Boolean) {
        c.c.fav.observe(c) { if (it != null) onLoaded() }
    }

    override fun onLoaded() {
        c.vm.favourites = c.c.fav.value?.toList()?.sortedBy { it.user } ?: listOf()
        c.vm.favCount.value = c.vm.favourites.size
        super.onLoaded()
    }
}
