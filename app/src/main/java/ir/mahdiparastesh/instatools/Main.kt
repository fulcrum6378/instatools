package ir.mahdiparastesh.instatools

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.viewpager2.adapter.FragmentStateAdapter
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools

// adb connect 192.168.1.20:
// TODO: WE'RE BLOCKED

class Main : BaseActivity() {
    lateinit var b: MainBinding
    lateinit var db: Database
    lateinit var dao: Database.DAO
    val myUser = "fulcrum1378"
    val myId = "8337021434"

    companion object {
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
        db = Database.DbFile.build(c, myUser).also { dao = it.dao() }

        // Paging
        b.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = pages.size
            override fun createFragment(i: Int): Fragment = when (i) {
                0 -> Unfollowers(this@Main)
                1 -> Saver(this@Main)
                2 -> Direct(this@Main)
                else -> throw IllegalArgumentException("What the fuck?")
            }
        }
        b.pager.setPageTransformer { _, fl ->
            if (b.bnv.selectedItemId != pages[b.pager.currentItem])
                b.bnv.selectedItemId = pages[b.pager.currentItem]
        }
        b.bnv.itemIconTintList = null // It seems impossible to do this via XML.
        b.bnv.setOnItemSelectedListener {
            b.pager.setCurrentItem(pages.indexOf(it.itemId), true); true
        }
        val ca = resources.getIntArray(R.array.CA)
        UiTools.bnvTitles(b.bnv).forEachIndexed { i, it -> it.setTextColor(ca[i]) }
    }
}
