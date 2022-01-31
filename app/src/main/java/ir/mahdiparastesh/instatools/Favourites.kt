package ir.mahdiparastesh.instatools

import android.os.Bundle
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.more.BaseActivity

class Favourites : BaseActivity() {
    private lateinit var b: FavouritesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FavouritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.fvTitle)
    }
}
