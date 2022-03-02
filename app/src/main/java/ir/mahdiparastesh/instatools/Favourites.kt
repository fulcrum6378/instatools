package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Favourites : BaseActivity() {
    private lateinit var b: FavouritesBinding

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FavouritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.favourites)

        b.refresher.setOnRefreshListener { load() }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
                updateJumper()
            }
        })
        b.jumper.setOnClickListener { b.rv.smoothScrollToPosition(0) }
        b.jumper.translationY = UiTools.jumperTrans(this)
        shouldShowJumper.observe(this) {
            anJumper?.cancel()
            anJumper = UiTools.anJumper(this, b.jumper, it)
        }
        b.empty.typeface = fontRegular

        if (m.fav != null) adapt() else load()
    }

    override fun onResume() {
        super.onResume()
        if (notFirstResume) load()
    }

    private fun load() {
        CoroutineScope(Dispatchers.IO).launch {
            m.fav = ArrayList(dao.favourites())
            withContext(Dispatchers.Main) {
                b.refresher.isRefreshing = false
                adapt()
            }
        }
    }

    private fun adapt() {
        if (m.fav!!.isNotEmpty()) b.rv.adapter = ListFav(this@Favourites)
        else {
            b.rv.vis(false)
            b.empty.vis(true)
        }
    }

    private var shouldShowJumper = MutableLiveData(false)
    private var anJumper: ObjectAnimator? = null
    private fun updateJumper() {
        (b.rv.computeVerticalScrollOffset() > dm.heightPixels)
            .apply { if (this != shouldShowJumper.value) shouldShowJumper.value = this }
    }
}
