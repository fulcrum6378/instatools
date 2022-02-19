package ir.mahdiparastesh.instatools

import android.os.Bundle
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.more.BaseActivity

class Favourites : BaseActivity() {
    private lateinit var b: FavouritesBinding
    override val menuRes: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FavouritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.favourites)
        db = Database.build(c, m.acc!!.id.toString()).also { dao = it.dao() }

        Thread {
            m.fav = ArrayList(dao.favourites())
            b.rv.adapter = ListFav(this)
        }.start()
    }
}
