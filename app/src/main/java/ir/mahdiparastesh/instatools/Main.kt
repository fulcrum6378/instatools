package ir.mahdiparastesh.instatools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.text.SpannableString
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import ir.mahdiparastesh.instatools.Settings.Companion.spMainPage
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListSch
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.view.CustomTypefaceSpan
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

// adb connect 192.168.1.20:

@SuppressLint("ApplySharedPref")
class Main : BaseActivity(true), Toolbar.OnMenuItemClickListener {
    lateinit var b: MainBinding
    private lateinit var db: Database
    lateinit var dao: Database.DAO

    override val menuRes = R.menu.main_tlb
    private lateinit var toggleNav: ActionBarDrawerToggle
    private var searchInput: SearchView.SearchAutoComplete? = null
    private var searchClose: ImageView? = null
    var schRes: Array<Rest.ItemUser>? = null
    private var page1: PageUnf? = null
    private var page2: PageSvd? = null
    private var page3: PageBox? = null
    private var anTheme: ValueAnimator? = null
    private lateinit var bg: IntArray
    private lateinit var ca: IntArray
    private val colorBG = MutableLiveData<Int?>(null)

    private var interstitialAd: InterstitialAd? = null
    private var interstitialListener = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {}
        override fun onAdFailedToShowFullScreenContent(adError: AdError?) {}
        override fun onAdShowedFullScreenContent() {
            interstitialAd = null
        }
    }

    companion object {
        const val EXTRA_TURN_TO_PAGE = "EXTRA_TURN_TO_PAGE"
        val bnvButtons = arrayOf(R.id.to_unfollowers, R.id.to_saved, R.id.to_direct)
        var guest = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = PageFactory()
        super.onCreate(savedInstanceState)
        if (gsp.contains(Login.spAccount))
            m.acc = Account.selected(this)
        if (!gsp.contains(Login.spAccount) || m.acc == null) {
            goTo(Login::class, true)
            return
        }
        b = MainBinding.inflate(layoutInflater)
        setContentView(b.root)
        sp = Persistent.initSp(c, m.acc)
        guest = m.acc!!.id == -1L
        if (m.acc!!.id > -1L)
            db = Database.build(c, m.acc!!.id.toString()).also { dao = it.dao() }

        // Ads
        MobileAds.initialize(this) {
            Delay(10000) {
                InterstitialAd.load(
                    this, "ca-app-pub-9457309151954418/5399016395",
                    //ca-app-pub-3940256099942544/1033173712
                    AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            interstitialAd = null
                        }

                        override fun onAdLoaded(ad: InterstitialAd) {
                            interstitialAd = ad.apply {
                                fullScreenContentCallback = interstitialListener
                            }
                        }
                    })
            }
        }

        // Toolbar & Navigation
        toolbar(b.toolbar, R.string.app_name, font = font(getString(R.string.font_logo)))
        toggleNav = ActionBarDrawerToggle(
            this, b.root, b.toolbar, R.string.navOpen, R.string.navClose
        ).apply {
            b.root.addDrawerListener(this)
            isDrawerIndicatorEnabled = true
            syncState()
        }
        b.nav.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.mnDownloads -> goTo(Downloads::class)
                R.id.mnFavourites -> goTo(Favourites::class)
                R.id.mnGSettings -> {
                    startActivity(Intent(this, Settings::class.java)
                        .apply { putExtra(Settings.EXTRA_IS_GLOBAL, true) })
                    true
                }
                R.id.mnSettings -> goTo(Settings::class)
                R.id.mnSwitchAccount -> {
                    if (ForegroundService.srvComps.any { it.active }) AlertDialog.Builder(c).apply {
                        setTitle(R.string.backgroundTasks)
                        setMessage(R.string.terminateBgTasks)
                        setNegativeButton(R.string.no, null)
                        setPositiveButton(R.string.yes) { _, _ ->
                            ForegroundService.terminateTasks(c)
                            switchAcc()
                        }
                    }.create().show() else switchAcc()
                    true
                }
                R.id.mnSignOut -> {
                    val bd = AlsoDeleteDataBinding.inflate(layoutInflater)
                    // TODO: BODY: one_tap_app_login=1&user_id=28826142999
                    AlertDialog.Builder(this).apply {
                        setTitle(R.string.signOut)
                        setMessage(R.string.signOutSure)
                        setView(bd.root)
                        setNegativeButton(R.string.no, null)
                        setPositiveButton(R.string.yes) { _, _ ->
                            ForegroundService.terminateTasks(c)
                            if (bd.root.isChecked) {
                                Settings.deleteDb(m.acc!!.id.toString())
                                Settings.deleteSp(this@Main)
                            }
                            Account.save(
                                c, Account.load(c).apply { removeAll { it.id == m.acc!!.id } })
                            gsp.edit().remove(Login.spAccount).commit()
                            m.acc = null
                            goTo(Login::class, true)
                        }
                    }.create().show()
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
        }
        if (guest) arrayOf(R.id.mnSettings, R.id.mnSignOut)
            .forEach { b.nav.menu.findItem(it)?.isEnabled = false }
        b.nav.menu.forEach {
            val mNewTitle = SpannableString(it.title)
            val col = colorAc.value ?: color(R.color.defCA)
            mNewTitle.setSpan(
                CustomTypefaceSpan(
                    "", fontRegular, resources.getDimension(R.dimen.navFont),
                    if (it.isEnabled) col else weaken(col)
                ), 0, mNewTitle.length, SpannableString.SPAN_INCLUSIVE_INCLUSIVE
            )
            it.title = mNewTitle
        }

        // Paging
        m.currentPage.value = sp?.getInt(spMainPage, 0) ?: 0
        if (page1 == null) page1 = PageUnf(this)
        if (page2 == null) page2 = PageSvd(this)
        if (page3 == null) page3 = PageBox(this)
        loadPages()
        bg = resources.getIntArray(R.array.BG)
        ca = resources.getIntArray(R.array.CA)
        b.bnv.itemIconTintList = null // It seems impossible to do this via XML.
        b.bnv.selectedItemId = bnvButtons[m.currentPage.value!!]
        b.bnv.setOnItemSelectedListener { turnToPage(bnvButtons.indexOf(it.itemId)) }
        m.currentPage.observe(this) {
            pages()[it].updateShadow()
            pages()[it].updateJumper()
        }

        // Theming
        if (night) {
            colorBG.observe(this) {
                if (it == null) return@observe
                window.decorView.setBackgroundColor(it)
                window.statusBarColor = it
                window.navigationBarColor = it
                b.bnv.setBackgroundColor(it)
                b.nav.setBackgroundColor(it)
                b.searchRes.setBackgroundColor(it)
                page2?.b?.expanded?.setBackgroundColor(it)
            }
            colorBG.value = bg[m.currentPage.value!!]
        } else {
            colorAc.observe(this) {
                if (it == null) return@observe
                val cf = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
                b.toolbar.navigationIcon?.colorFilter = cf
                b.toolbar.menu.forEach { item -> item.icon?.colorFilter = cf }
                tbTitle?.setTextColor(it)
                searchInput?.setTextColor(it)
                searchInput?.setHintTextColor(weaken(it))
                searchClose?.colorFilter = cf
            }
            colorAc.value = ca[m.currentPage.value!!]
        }
        UiTools.bnvTitles(b.bnv).forEachIndexed { i, it ->
            it.setTextColor(ca[i / 2])
            it.typeface = fontLight
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.extras?.getInt(EXTRA_TURN_TO_PAGE)?.let { turnToPage(it) }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.recreateMain) {
            recreate(); return; }
        interstitialAd?.show(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        b.toolbar.setOnMenuItemClickListener(this)
        colorAc.value?.let {
            val cf = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
            searchInput?.setTextColor(it)
            searchInput?.setHintTextColor(weaken(it))
            searchClose?.colorFilter = cf
            toolbar.menu.forEach { item -> item.icon?.colorFilter = cf }
        }

        (b.toolbar.menu.findItem(R.id.mtSearch).actionView as SearchView).apply {
            searchInput = findViewById(androidx.appcompat.R.id.search_src_text)
            // useless: search_button, search_go_btn, search_mag_icon
            searchClose = findViewById(androidx.appcompat.R.id.search_close_btn)
            searchInput?.typeface = fontRegular
            searchInput?.setHint(R.string.mtSearch)
            searchInput?.textSize = resources.getDimension(R.dimen.searchFont)

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = true

                @SuppressLint("NotifyDataSetChanged")
                override fun onQueryTextChange(newText: String): Boolean {
                    if (newText == "") {
                        schRes = null
                        if (b.searchRes.adapter == null)
                            b.searchRes.adapter = ListSch(this@Main)
                        else b.searchRes.adapter?.notifyDataSetChanged()
                        return true
                    }
                    Api<Rest.Search>(
                        this@Main, Api.Type.SEARCH.url.format(newText), Rest.Search::class,
                        null, cache = true
                    ) { res ->
                        schRes = res.users.sortedBy { it.position }.toTypedArray()
                        b.searchRes.adapter?.notifyDataSetChanged()
                    }
                    return true
                }
            })
        }
        b.toolbar.menu.findItem(R.id.mtSearch).setOnActionExpandListener(object :
            MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                b.searchRes.vis()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                b.searchRes.vis(false)
                b.searchRes.adapter = null
                schRes = null
                return true
            }
        })
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.mtSearch -> {
            true
        }
        else -> false
    }

    override fun onBackPressed() {
        if (pages()[m.currentPage.value!!].goBack()) return
        super.onBackPressed()
    }

    override fun onDestroy() {
        page1 = null
        page2 = null
        page3 = null
        m.accountSwitched()
        super.onDestroy()
    }


    private fun loadPages() = pages().forEachIndexed { i, it ->
        transFrag().apply {
            if (it.isAdded) remove(it)
            add(R.id.frame, it)
            if (i != m.currentPage.value) hide(it)
            commit()
        }
    }

    private fun turnToPage(i: Int): Boolean {
        if (i == m.currentPage.value) return true
        val lastPage = m.currentPage.value!!
        m.currentPage.value = i
        transFrag(lastPage, m.currentPage.value!!)
            .hide(pages()[lastPage])
            .show(pages()[m.currentPage.value!!])
            .commit()
        sp?.edit()?.putInt(spMainPage, m.currentPage.value!!)?.commit()

        anTheme?.cancel()
        val col = if (night) bg else ca
        anTheme = ValueAnimator.ofArgb(col[lastPage], col[m.currentPage.value!!]).apply {
            duration = resources.getInteger(R.integer.transFrag).toLong()
            addUpdateListener {
                if (night) colorBG.value = it.animatedValue as Int
                else colorAc.value = it.animatedValue as Int
            }
            start()
        }
        b.toolbar.menu.findItem(R.id.mtSearch)?.collapseActionView()
        return true
    }

    private fun pages() = arrayOf(page1!!, page2!!, page3!!)

    private var isSelective = false
    fun selective(bb: Boolean) {
        if (isSelective == bb) return
        isSelective = bb
        b.toolbar.menu.clear()
        b.toolbar.inflateMenu(
            when {
                //bb && m.currentPage.value == 0 -> R.menu.main_tlb_unf_select
                bb && m.currentPage.value == 1 -> R.menu.main_tlb_svd_select
                //bb && m.currentPage.value == 2 -> R.menu.main_tlb_box_select
                else -> R.menu.main_tlb
            }
        )
        b.toolbar.setOnMenuItemClickListener(
            if (bb) arrayOf<Toolbar.OnMenuItemClickListener>(
                page1!!, page2!!, page3!!
            )[m.currentPage.value!!] else this
        )
        b.bnv.menu.forEach { it.isEnabled = !bb }
        if (!night) colorAc.value = colorAc.value
    }

    private fun transFrag(from: Int? = null, to: Int? = null) =
        supportFragmentManager.beginTransaction().apply {
            if (from == null || to == null || from == to) return@apply
            if (if (!dirRtl) from < to else from > to) setCustomAnimations(
                R.anim.enter_from_right,
                R.anim.exit_to_left,
                R.anim.enter_from_left,
                R.anim.exit_to_right
            ) else setCustomAnimations(
                R.anim.enter_from_left,
                R.anim.exit_to_right,
                R.anim.enter_from_right,
                R.anim.exit_to_left
            )
        }

    private fun switchAcc() {
        gsp.edit().remove(Login.spAccount).commit()
        goTo(Login::class, true)
    }

    private inner class PageFactory : FragmentFactory() {
        override fun instantiate(loader: ClassLoader, name: String): Fragment = when (name) {
            PageUnf::class.java.name -> {
                if (page1 != null && page1?.isAdded == true)
                    transFrag().remove(page1!!).commit()
                PageUnf(this@Main).also { page1 = it }
            }
            PageSvd::class.java.name -> {
                if (page2 != null && page2?.isAdded == true)
                    transFrag().remove(page2!!).commit()
                PageSvd(this@Main).also { page2 = it }
            }
            PageBox::class.java.name -> {
                if (page3 != null && page3?.isAdded == true)
                    transFrag().remove(page3!!).commit()
                PageBox(this@Main).also { page3 = it }
            }
            else -> super.instantiate(loader, name)
        }
    }
}
