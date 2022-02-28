package ir.mahdiparastesh.instatools

import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

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

        Thread {
            m.fav = ArrayList(dao.favourites())
            b.rv.adapter = ListFav(this)
        }.start()

        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
            }
        })
    }
}
