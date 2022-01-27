package ir.mahdiparastesh.instatools

import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import ir.mahdiparastesh.instatools.data.GlobalDb
import ir.mahdiparastesh.instatools.data.PersonalDb
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.UiTools

class Main : BaseActivity(true) {
    private lateinit var b: MainBinding
    private lateinit var gDb: GlobalDb
    lateinit var gDao: GlobalDb.DAO
    private lateinit var pDb: PersonalDb
    lateinit var pDao: PersonalDb.DAO
    private lateinit var bg: IntArray
    private lateinit var ca: IntArray
    private lateinit var toggleNav: ActionBarDrawerToggle
    private var page1: PageUnf? = null
    private var page2: PageSvd? = null
    private var page3: PageBox? = null
    private var anTheme: ValueAnimator? = null
    private var currentPage = 0

    companion object {
        val pages = arrayOf(R.id.to_unfollowers, R.id.to_saved, R.id.to_direct)
        var guest = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = PageFactory()
        super.onCreate(savedInstanceState)
        if (m.acc == null) {
            if (!sp.contains(Login.spAccount)) {
                goTo(Login::class)
                return
            } else {
                gDb = GlobalDb.build(c).also { gDao = it.dao() }
                m.acc = Login.gatherData(this, gDao)
            }
        }
        if (m.acc!!.id == -1L) guest = true
        else pDb = PersonalDb.build(c, m.acc!!.id.toString()).also { pDao = it.dao() }
        b = MainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Toolbar & Navigation
        toolbar(b.toolbar, R.string.app_name)
        toggleNav = ActionBarDrawerToggle(
            this, b.root, b.toolbar, R.string.navOpen, R.string.navClose
        ).apply {
            b.root.addDrawerListener(this)
            isDrawerIndicatorEnabled = true
            syncState()
        }
        b.nav.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.mnDownloads -> goTo(Downloads::class)
                R.id.mnSettings -> goTo(Settings::class)
                R.id.mnSwitchAccount -> {
                    sp.edit().remove(Login.spAccount).apply()
                    goTo(Login::class, true)
                }
                R.id.mnSignOut -> {
                    // TODO: ASK FIRST
                    // TODO: WHAT TO DO!?!?
                    sp.edit().remove(Login.spAccount).apply()
                    goTo(Login::class, true)
                }
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
        if (page1 == null) page1 = PageUnf(this)
        if (page2 == null) page2 = PageSvd(this)
        if (page3 == null) page3 = PageBox(this)
        loadPages()
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

            anTheme?.cancel()
            anTheme = null
            val col = if (night) bg else ca
            anTheme = ValueAnimator.ofArgb(col[lastPage], col[currentPage]).apply {
                duration = 400L
                addUpdateListener {
                    if (night) nightTheme(it.animatedValue as Int)
                    else dayTheme(it.animatedValue as Int)
                }
                start()
            }
            true
        }
        if (night) nightTheme(bg[0]) else dayTheme(ca[0])
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
            if (i != currentPage) hide(it)
            commit()
        }
    }

    private fun pages() = arrayOf(page1!!, page2!!, page3!!)

    private fun dayTheme(colour: Int) {
        val cf = PorterDuffColorFilter(colour, PorterDuff.Mode.SRC_IN)
        b.toolbar.navigationIcon?.colorFilter = cf
        b.toolbar.menu.findItem(R.id.mtSearch).icon.colorFilter = cf
        tbTitle?.setTextColor(colour)
    }

    private fun nightTheme(colour: Int) {
        window.decorView.setBackgroundColor(colour)
        window.statusBarColor = colour
        window.navigationBarColor = colour
        b.bnv.setBackgroundColor(colour)
        b.nav.setBackgroundColor(colour)
    }

    private inner class PageFactory : FragmentFactory() {
        override fun instantiate(loader: ClassLoader, name: String): Fragment = when (name) {
            PageUnf::class.java.name -> {
                if (page1 != null && page1?.isAdded == true)
                    supportFragmentManager.beginTransaction().remove(page1!!).commit()
                PageUnf(this@Main).also { page1 = it }
            }
            PageSvd::class.java.name -> {
                if (page2 != null && page2?.isAdded == true)
                    supportFragmentManager.beginTransaction().remove(page2!!).commit()
                PageSvd(this@Main).also { page2 = it }
            }
            PageBox::class.java.name -> {
                if (page3 != null && page3?.isAdded == true)
                    supportFragmentManager.beginTransaction().remove(page3!!).commit()
                PageBox(this@Main).also { page3 = it }
            }
            else -> super.instantiate(loader, name)
        }
    }
}
