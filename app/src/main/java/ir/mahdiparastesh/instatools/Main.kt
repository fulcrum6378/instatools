package ir.mahdiparastesh.instatools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.forEach
import androidx.lifecycle.MutableLiveData
import com.android.volley.Request
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import ir.mahdiparastesh.instatools.Settings.Companion.spMainPage
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.databinding.MainNavHeaderBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListSch
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.TriplePageActivity
import ir.mahdiparastesh.instatools.view.MaterialMenu.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.accFromUrl
import ir.mahdiparastesh.instatools.view.UiTools.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import kotlin.reflect.KClass

// adb connect 192.168.1.20:

class Main : TriplePageActivity<PageUnf, PageSvd, PageBox>(),
    NavigationView.OnNavigationItemSelectedListener {
    lateinit var b: MainBinding
    private lateinit var toggleNav: ActionBarDrawerToggle
    private lateinit var bh: MainNavHeaderBinding
    private var anTheme: ValueAnimator? = null
    private val bg: IntArray by lazy { resources.getIntArray(R.array.BG) }
    private val ca: IntArray by lazy { resources.getIntArray(R.array.CA) }
    private val colorBG = MutableLiveData<Int?>(null)
    private val bnvButtons = arrayOf(R.id.to_unfollowers, R.id.to_saved, R.id.to_direct)

    private var searchInput: SearchView.SearchAutoComplete? = null
    private var searchClose: ImageView? = null
    var schRes: Array<Rest.ItemUser>? = null
    private var searcher: Api<Rest.Search>? = null
    private var searchErrored = false

    override val menuRes = R.menu.main_tlb
    override val com: ActivityCompanion get() = Companion
    override val currentPage: MutableLiveData<Int> get() = m.currentPage
    override val aKlass: KClass<PageUnf> = PageUnf::class
    override val bKlass: KClass<PageSvd> = PageSvd::class
    override val cKlass: KClass<PageBox> = PageBox::class
    override val mode: TripleMode = TripleMode.FRAGMENT_MANAGER
    override fun aCreate(): PageUnf = PageUnf(this)
    override fun bCreate(): PageSvd = PageSvd(this)
    override fun cCreate(): PageBox = PageBox(this)
    override fun defPage(): Int = intent.extras?.getInt(EXTRA_TURN_TO_PAGE)
        ?: sp?.getInt(spMainPage, Settings.defSpMainPage)
        ?: Settings.defSpMainPage

    companion object : ActivityCompanion() {
        var guest = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (m.acc == null) {
            goTo(Login::class, true)
            return; }
        b = MainBinding.inflate(layoutInflater)
        setContentView(b.root)
        guest = m.acc!!.id == -1L
        toolbar(b.toolbar, R.string.app_name, font = font(getString(R.string.font_logo)))
        createPages()

        // Bottom Navigation Bar
        b.bnv.itemIconTintList = null // It seems impossible to do this via XML.
        b.bnv.selectedItemId = bnvButtons[m.currentPage.value!!]
        b.bnv.setOnItemSelectedListener { turnToPage(bnvButtons.indexOf(it.itemId)) }
        m.unfollowers.observe(this) { bnvBadge(0, it?.size) }

        // Theming
        if (night()) {
            colorBG.observe(this) {
                if (it == null) return@observe
                window.decorView.setBackgroundColor(it)
                window.statusBarColor = it
                window.navigationBarColor = it
                b.nav.setBackgroundColor(it)
                b.toolbar.menu.forEach { item -> item.stylise(this@Main) }
                b.searchRes.setBackgroundColor(it)
                b.bnv.setBackgroundColor(it)
                page2?.b?.expanded?.root?.setBackgroundColor(it)
            }
            colorBG.value = bg[m.currentPage.value!!]
        } else {
            colorAc.observe(this) {
                if (it == null) return@observe
                val cf = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
                b.toolbar.navigationIcon?.colorFilter = cf
                b.toolbar.overflowIcon?.colorFilter = cf
                b.toolbar.menu.forEach { item ->
                    item.icon?.colorFilter = cf
                    item.stylise(this@Main, it)
                }
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

        // Navigation
        toggleNav = ActionBarDrawerToggle(
            this, b.root, b.toolbar, R.string.navOpen, R.string.navClose
        ).apply {
            b.root.addDrawerListener(this)
            isDrawerIndicatorEnabled = true
            syncState()
        }
        bh = MainNavHeaderBinding.bind(b.nav.getHeaderView(0) as ConstraintLayout)
        if (m.acc!!.id != -1L) {
            Glide.with(c).load(m.acc!!.pict).into(bh.pict)
            bh.user.text = m.acc!!.user
            bh.user.typeface = fontLight
            if (!m.acc!!.name.isNullOrBlank()) {
                bh.name.text = m.acc!!.name
                bh.name.typeface = fontRegular
            } else bh.name.vis(false)
            bh.ll.setOnClickListener { UiTools.openProfile(this, m.acc!!.user!!) }
        } else bh.root.vis(false)
        b.nav.setNavigationItemSelectedListener(this)
        if (guest) arrayOf(R.id.mnMassFollower, R.id.mnSettings, R.id.mnSignOut)
            .forEach { b.nav.menu.findItem(it)?.isEnabled = false }
        b.nav.menu.forEach { it.stylise(this, color(R.color.defCA), R.dimen.navFont) }

        // Miscellaneous
        Favourites.FavLoader(this).start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.extras?.getInt(EXTRA_TURN_TO_PAGE)?.let { turnToPage(it) }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.recreateMain) {
            Settings.recreateMain = false
            recreate(); return; }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.mnDownloads -> goTo(Downloads::class)
        R.id.mnFavourites -> goTo(Favourites::class)
        R.id.mnMassFollower -> goTo(MassFollower::class)
        R.id.mnGSettings -> goTo(Settings::class) { putExtra(Settings.EXTRA_IS_GLOBAL, true) }
        R.id.mnSettings -> goTo(Settings::class)
        R.id.mnSwitchAccount -> if (ForegroundService.anyRunning()) {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.backgroundTasks)
                setMessage(R.string.terminateBgTasks)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    ForegroundService.terminateTasks(c)
                    switchAcc()
                }
            }.show().stylise(this)
            true
        } else {
            switchAcc(); true; }
        R.id.mnSignOut -> {
            val bd = AlsoDeleteDataBinding.inflate(layoutInflater)
            bd.root.typeface = fontRegular
            AlertDialog.Builder(this).apply {
                setTitle(R.string.signOut)
                setMessage(R.string.signOutSure)
                setView(bd.root)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    Api<Rest.Signing>(
                        this@Main, Api.Type.SIGN_OUT.url, Rest.Signing::class, null,
                        method = Request.Method.POST,
                        body = "one_tap_app_login=1&user_id=${m.acc!!.id}",
                        onError = { signOut(bd.root.isChecked) }
                    ) { signOut(bd.root.isChecked) }
                }
            }.show().stylise(this)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
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
            searchInput?.textSize = dimen(R.dimen.searchFont)

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
                    UiTools.ACC_FROM_URL.forEach { host ->
                        newText.accFromUrl(host)?.let {
                            if (searchInput == null) return@let
                            searchInput?.setText(it)
                            return true
                        }
                    }

                    if (searchErrored) b.searchStatus.setAnimation(R.raw.pending)
                    searchErrored = false
                    b.searchStatus.playAnimation()
                    b.searchStatus.vis()
                    searcher?.cancel()
                    searcher = Api(
                        this@Main, Api.Type.SEARCH.url.format(newText), Rest.Search::class,
                        null, cache = true, onError = {
                            searchErrored = true
                            b.searchStatus.setAnimation(R.raw.failed)
                        }
                    ) { res ->
                        b.searchStatus.vis(false)
                        b.searchStatus.pauseAnimation()
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
                b.searchStatus.vis(false)
                b.searchStatus.pauseAnimation()
                return true
            }
        })
        return true
    }

    override fun turnToPage(i: Int): Boolean {
        if (!super.turnToPage(i)) return true
        sp?.edit()?.putInt(spMainPage, m.currentPage.value!!)?.commit()

        anTheme?.cancel()
        val col = if (night()) bg else ca
        anTheme = ValueAnimator.ofArgb(col[lastPage], col[m.currentPage.value!!]).apply {
            duration = resources.getInteger(R.integer.transFrag).toLong()
            addUpdateListener {
                if (night()) colorBG.value = it.animatedValue as Int
                else colorAc.value = it.animatedValue as Int
            }
            start()
        }
        b.toolbar.menu.findItem(R.id.mtSearch)?.collapseActionView()
        return true
    }

    fun bnvBadge(i: Int, num: Int?) = b.bnv.getOrCreateBadge(bnvButtons[i]).apply {
        isVisible = num != null
        number = num ?: 0
        backgroundColor = ca[i]
        badgeTextColor = if (!night()) bg[i] else color(R.color.defBG)
    }

    override fun selective(bb: Boolean): Boolean {
        if (!super.selective(bb)) return false
        b.bnv.menu.forEach { it.isEnabled = !bb }
        if (!night()) colorAc.value = colorAc.value
        return true
    }

    private fun signOut(bd: Boolean) {
        ForegroundService.terminateTasks(c)
        if (bd) {
            Settings.deleteDb(m.acc!!.id.toString())
            Settings.deleteSp(this@Main)
        }
        Account.save(c, Account.load(c).apply { removeAll { it.id == m.acc!!.id } })
        switchAcc()
    }

    private var exiting = false
    override fun onBackPressed() {
        if (b.root.isDrawerOpen(GravityCompat.START)) {
            b.root.closeDrawer(GravityCompat.START)
            toggleNav.syncState()
            return; }
        if (pageGoBack()) return
        if (!exiting) {
            exiting = true
            Delay(4000L) { exiting = false }
            Toast.makeText(c, R.string.toExit, Toast.LENGTH_SHORT).show()
            return; }
        super.onBackPressed() // Do NOT kill the process
    }
}
