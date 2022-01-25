package ir.mahdiparastesh.instatools

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import ir.mahdiparastesh.instatools.data.PersonalDb
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools

// adb connect 192.168.1.20:

class Main : BaseActivity(true) {
    private lateinit var b: MainBinding
    private lateinit var db: PersonalDb
    lateinit var dao: PersonalDb.DAO
    private lateinit var bg: IntArray
    private lateinit var ca: IntArray
    private lateinit var toggleNav: ActionBarDrawerToggle
    private var page1: Fragment? = null
    private var page2: Fragment? = null
    private var page3: Fragment? = null
    private var anTheme: ValueAnimator? = null
    private var currentPage = 0

    companion object {
        val pages = arrayOf(R.id.to_unfollowers, R.id.to_saver, R.id.to_direct)
        var guest = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = PageFactory()
        super.onCreate(savedInstanceState)
        b = MainBinding.inflate(layoutInflater)
        setContentView(b.root)
        if (page1 == null) page1 = Unfollowers(this)
        if (page2 == null) page2 = Saver(this)
        if (page3 == null) page3 = Direct(this)
        loadPages()
        if (m.id == null) guest = true
        else db = PersonalDb.build(c, m.id!!).also { dao = it.dao() }

        // Toolbar & Navigation
        toolbar(b.toolbar, R.string.app_name)
        toggleNav = ActionBarDrawerToggle(
            this, b.root, b.toolbar, R.string.navOpen, R.string.navClose
        ).apply {
            b.root.addDrawerListener(this)
            isDrawerIndicatorEnabled = true
            syncState()
        }
        //b.toolbar.navigationIcon?.colorFilter = pdcf()
        b.nav.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.mnSettings -> {
                    startActivity(Intent(this, Settings::class.java)); true; }
                else -> super.onOptionsItemSelected(it)
            }
        }
        /*b.nav.menu.forEach {
            val mNewTitle = SpannableString(it.title)
            mNewTitle.setSpan(
                CustomTypefaceSpan("", font1, dm.density * 16f), 0,
                mNewTitle.length, SpannableString.SPAN_INCLUSIVE_INCLUSIVE
            )
            it.title = mNewTitle
        }*/

        // Paging
        bg = resources.getIntArray(R.array.BG)
        ca = resources.getIntArray(R.array.CA)
        b.bnv.itemIconTintList = null // It seems impossible to do this via XML.
        b.bnv.setOnItemSelectedListener { item ->
            val lastPage = currentPage
            currentPage = pages.indexOf(item.itemId)
            if (lastPage == currentPage) return@setOnItemSelectedListener true
            supportFragmentManager.beginTransaction()
                .hide(pages()[lastPage])
                .show(pages()[currentPage])
                .commit()

            if (!night) return@setOnItemSelectedListener true
            anTheme?.cancel()
            anTheme = null
            anTheme = ValueAnimator.ofArgb(bg[lastPage], bg[currentPage]).apply {
                duration = 500L
                addUpdateListener { theme(it.animatedValue as Int) }
                start()
            }
            true
        }
        if (night) theme(bg[0])
        UiTools.bnvTitles(b.bnv).forEachIndexed { i, it -> it.setTextColor(ca[i]) }
    }

    override fun onDestroy() {
        page1 = null
        page2 = null
        page3 = null
        super.onDestroy()
    }

    private fun loadPages() = pages().forEachIndexed { i, it ->
        supportFragmentManager.beginTransaction().apply {
            if (it.isAdded) remove(it)
            add(R.id.frame, it)
            if (i != i) hide(it)
            commit()
        }
    }

    private fun pages() = arrayOf(page1!!, page2!!, page3!!)

    private fun theme(colour: Int) {
        window.decorView.setBackgroundColor(colour)
        window.statusBarColor = colour
        window.navigationBarColor = colour
        b.bnv.setBackgroundColor(colour)
    }

    private inner class PageFactory : FragmentFactory() {
        override fun instantiate(loader: ClassLoader, name: String): Fragment = when (name) {
            Unfollowers::class.java.name -> {
                if (page1 != null && page1?.isAdded == true)
                    supportFragmentManager.beginTransaction().remove(page1!!).commit()
                Unfollowers(this@Main).also { page1 = it }
            }
            Saver::class.java.name -> {
                if (page2 != null && page2?.isAdded == true)
                    supportFragmentManager.beginTransaction().remove(page2!!).commit()
                Saver(this@Main).also { page2 = it }
            }
            Direct::class.java.name -> {
                if (page3 != null && page3?.isAdded == true)
                    supportFragmentManager.beginTransaction().remove(page3!!).commit()
                Direct(this@Main).also { page3 = it }
            }
            else -> super.instantiate(loader, name)
        }
    }
}
