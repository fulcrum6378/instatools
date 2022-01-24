package ir.mahdiparastesh.instatools

import android.os.Bundle
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.viewpager2.adapter.FragmentStateAdapter
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools
import kotlin.math.abs
import kotlin.math.max

class Main : BaseActivity() {
    private lateinit var b: MainBinding
    private lateinit var db: Database
    lateinit var dao: Database.DAO
    private lateinit var bg: IntArray
    private lateinit var ca: IntArray
    private var vpTransStable = 0
    private var vpStableDirToEnd = true
    private var vpPrevTrans = 0f
    var guest = false

    companion object {
        const val MIN_SCALE = 0.85f
        const val MIN_ALPHA = 0.5f
        val pages = arrayOf(R.id.unfollowers, R.id.saver, R.id.direct)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = object : FragmentFactory() {
            override fun instantiate(loader: ClassLoader, name: String): Fragment = when (name) {
                Unfollowers::class.java.name -> Unfollowers(this@Main)
                Saver::class.java.name -> Saver(this@Main)
                Direct::class.java.name -> Direct(this@Main)
                else -> super.instantiate(loader, name)
            }
        }
        super.onCreate(savedInstanceState)
        b = MainBinding.inflate(layoutInflater)
        setContentView(b.root)
        if (m.id == null) guest = true
        else db = Database.DbFile.build(c, m.id!!).also { dao = it.dao() }

        // Paging
        bg = resources.getIntArray(R.array.BG)
        ca = resources.getIntArray(R.array.CA)
        b.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = pages.size
            override fun createFragment(i: Int): Fragment = when (i) {
                0 -> Unfollowers(this@Main)
                1 -> Saver(this@Main)
                2 -> Direct(this@Main)
                else -> throw IllegalArgumentException("What the fuck?")
            }
        }
        //b.pager.isUserInputEnabled = false
        b.pager.setPageTransformer { v, fl ->
            val flu = abs(fl).coerceAtMost(1f)
            when { // ZoomOutPageTransformer
                fl < -1 -> v.alpha = 0f
                fl <= 1 -> {
                    val scaleFactor = max(MIN_SCALE, 1 - flu)
                    val verMargin = v.height * (1 - scaleFactor) / 2
                    val horMargin = v.width * (1 - scaleFactor) / 2
                    v.translationX =
                        if (fl < 0) horMargin - verMargin / 2
                        else horMargin + verMargin / 2
                    v.scaleX = scaleFactor
                    v.scaleY = scaleFactor
                    v.alpha = (MIN_ALPHA +
                            (((scaleFactor - MIN_SCALE) / (1 - MIN_SCALE)) * (1 - MIN_ALPHA)))
                }
                else -> v.alpha = 0f
            }

            if (b.bnv.selectedItemId != pages[b.pager.currentItem])
                b.bnv.selectedItemId = pages[b.pager.currentItem]

            if (!night) return@setPageTransformer
            if (flStable(flu)) vpTransStable = b.pager.currentItem
            else if (flStable(vpPrevTrans)) vpStableDirToEnd = flu > 0.5f
            if (abs(vpTransStable - b.pager.currentItem) == 2)
                vpTransStable += (b.pager.currentItem - vpTransStable) / 2
            try {
                theme(
                    if (flStable(flu)) bg[vpTransStable] else ColorUtils.blendARGB(
                        bg[vpTransStable],
                        bg[if (vpStableDirToEnd) vpTransStable - 1 else vpTransStable + 1],
                        if (!vpStableDirToEnd) 1f - flu else flu
                    )
                )
            } catch (ignored: ArrayIndexOutOfBoundsException) {
            }
            vpPrevTrans = flu
        }
        theme(bg[0])
        b.bnv.itemIconTintList = null // It seems impossible to do this via XML.
        b.bnv.setOnItemSelectedListener {
            b.pager.setCurrentItem(pages.indexOf(it.itemId), true); true
        }
        UiTools.bnvTitles(b.bnv).forEachIndexed { i, it -> it.setTextColor(ca[i]) }
    }

    private fun flStable(fl: Float) = fl == 0f || fl == 1f

    private fun theme(colour: Int) {
        window.decorView.setBackgroundColor(colour)
        window.statusBarColor = colour
        window.navigationBarColor = colour
        b.bnv.setBackgroundColor(colour)
    }
}
