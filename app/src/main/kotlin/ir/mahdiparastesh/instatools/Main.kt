package ir.mahdiparastesh.instatools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.forEach
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.volley.Request
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import ir.mahdiparastesh.instatools.Settings.Companion.clearCacheIfNecessary
import ir.mahdiparastesh.instatools.Settings.Companion.spMainPage
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.data.StorageCache
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.databinding.MainNavHeaderBinding
import ir.mahdiparastesh.instatools.databinding.SupportMethodsBinding
import ir.mahdiparastesh.instatools.frag.PageBox
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.json.GraphQl
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListSch
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Intelligence
import ir.mahdiparastesh.instatools.more.TriplePageActivity
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.accFromUrl
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class Main : TriplePageActivity<PageUnf, PageSvd, PageBox>(),
    NavigationView.OnNavigationItemSelectedListener {
    lateinit var b: MainBinding
    private lateinit var toggleNav: ActionBarDrawerToggle
    private lateinit var bh: MainNavHeaderBinding
    private var anTheme: ValueAnimator? = null
    val bg: IntArray by lazy { resources.getIntArray(R.array.BG) }
    val ca: IntArray by lazy { resources.getIntArray(R.array.CA) }
    private val colorBG = MutableLiveData<Int?>(null)
    val exportLauncher = launcherForResult { page3?.onActivityResult(it) }
    val mm: MyModel by viewModels()
    /*private lateinit var adBanner: AdView
    private var adBannerLoaded = false*/

    var searchInput: SearchView.SearchAutoComplete? = null
    private var searchClose: ImageView? = null
    var schRes: Array<Rest.ItemUser>? = null
    private val schQueue by lazy { Volley.newRequestQueue(c) }
    var searchErrored = false
    private val bnvButtons = arrayOf(R.id.to_unfollowers, R.id.to_saved, R.id.to_direct)
    private val popupThemes = arrayOf(
        R.style.Theme_InstaTools_Popup_Primary,
        R.style.Theme_InstaTools_Popup_Secondary,
        R.style.Theme_InstaTools_Popup_Tertiary
    )

    override val menuRes = R.menu.main_tlb
    override val com: ActivityCompanion get() = Companion
    override val currentPage: MutableLiveData<Int> get() = mm.currentPage
    override val aKlass = PageUnf::class
    override val bKlass = PageSvd::class
    override val cKlass = PageBox::class
    override val mode = TripleMode.FRAGMENT_MANAGER
    override fun defPage(): Int = if (Intelligence.galaxyCensor) 0 else
        intent.extras?.getInt(EXTRA_TURN_TO_PAGE)
            ?: sp?.getInt(spMainPage, Settings.defSpMainPage)
            ?: Settings.defSpMainPage

    class MyModel : ViewModel() {
        var unfollowers = MutableLiveData<ArrayList<Friend>?>(null)
        var saved: GraphQl.EdgeList? = null
        var dmInbox: Dm.Inbox? = null
        var dmThread: Dm.DmThread? = null
        val currentPage = MutableLiveData(Settings.defSpMainPage)
        var showingHelp = false

        fun accountSwitched() {
            unfollowers.value = null
            saved = null
            dmInbox = null
            dmThread = null
            showingHelp = false
        }
    }

    companion object : ActivityCompanion() {
        var guest = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Intelligence.onLaunch(this)) return
        if (!gsp.contains(Login.spAccount)) {
            goTo(Login::class, true); return; }
        b = MainBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(
            b.toolbar, R.string.app_name, Intelligence.censorText(getString(R.string.app_name))
        )

        // Bottom Navigation Bar
        b.bnv.itemIconTintList = null // It seems impossible to do this via XML.
        b.bnv.setOnItemSelectedListener { turnToPage(bnvButtons.indexOf(it.itemId)) }
        mm.unfollowers.observe(this) { bnvBadge(0, it?.filter { u -> !u.unfollowed }?.size) }

        // Theming
        if (night()) colorBG.observe(this) {
            if (it == null) return@observe
            window.decorView.setBackgroundColor(it)
            window.statusBarColor = it
            window.navigationBarColor = it
            styliseToolbar()
            onPrepareOptionsMenu(b.toolbar.menu)
            b.nav.setBackgroundColor(it)
            b.searchRes.setBackgroundColor(it)
            b.bnv.setBackgroundColor(it)
            if (page2?.bInitialised == true)
                page2?.b?.expanded?.root?.setBackgroundColor(it)
        } else colorAc.observe(this) {
            if (it == null) return@observe
            styliseToolbar()
            searchInput?.setTextColor(it)
            searchInput?.setHintTextColor(weaken(it))
            searchClose?.colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
        }
        UiTools.bnvTitles(b.bnv).forEachIndexed { i, it -> it.setTextColor(ca[i / 2]) }

        // Navigation
        toggleNav = /*object :*/ ActionBarDrawerToggle(
            this, b.root, b.toolbar, R.string.navOpen, R.string.navClose
        )/* {
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                if (::adBanner.isInitialized && !adBannerLoaded) {
                    adBanner.loadAd(AdRequest.Builder().build())
                    adBannerLoaded = true
                }
            }
        }*/.apply {
            b.root.addDrawerListener(this)
            isDrawerIndicatorEnabled = true
            syncState()
        }
        b.nav.setNavigationItemSelectedListener(this)
        if (guest) arrayOf(R.id.mnMassFollower, R.id.mnSettings, R.id.mnSignOut)
            .forEach { b.nav.menu.findItem(it)?.isEnabled = false }
        if (Intelligence.playCensor || Intelligence.galaxyCensor) {
            b.nav.menu.findItem(R.id.mnMassFollower)?.isVisible = false
            b.nav.menu.findItem(R.id.mnSupport)?.isVisible = false
            if (Intelligence.galaxyCensor)
                b.nav.menu.findItem(R.id.mnDownloads)?.isVisible = false
        }

        // Miscellaneous
        if (gsp.getInt(Settings.spUsedVersion, BuildConfig.VERSION_CODE) != BuildConfig.VERSION_CODE
            || !gsp.contains(Settings.spUsedVersion)
        ) gsp.edit { putInt(Settings.spUsedVersion, BuildConfig.VERSION_CODE) }
    }

    override fun onAccountSet() {
        if (m.acc == null) {
            goTo(Login::class, true)
            return; }
        super.onAccountSet()
        guest = m.acc!!.id == -1L
        onBuildUiBasedOnAccount()
    }

    override fun onBuildUiBasedOnAccount() {
        if (!isAccountSet || !::b.isInitialized || uiBuildBasedOnAccount) return
        super.onBuildUiBasedOnAccount()
        createPages()

        // Bottom Navigation Bar
        b.bnv.selectedItemId = bnvButtons[mm.currentPage.value!!]

        // Theming
        if (night()) colorBG.value = bg[mm.currentPage.value!!]
        else colorAc.value = ca[mm.currentPage.value!!]
        b.toolbar.popupTheme = popupThemes[mm.currentPage.value!!]

        // Navigation
        bh = MainNavHeaderBinding.bind(b.nav.getHeaderView(0) as ConstraintLayout)
        if (!guest) {
            Glide.with(c).load(m.acc!!.pict).into(bh.pict)
            bh.user.text = m.acc!!.user
            if (!m.acc!!.name.isNullOrBlank())
                bh.name.text = m.acc!!.name
            else bh.name.vis(false)
            bh.ll.setOnClickListener { UiTools.openProfile(this, m.acc!!.user!!) }
        } else bh.root.vis(false)

        // Miscellaneous
        if (m.files == null) StorageCache.load(this)
        Favourites.FavLoader(this).start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.extras?.getInt(EXTRA_TURN_TO_PAGE)?.let {
            if (::b.isInitialized) turnToPage(it)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.recreateMain) {
            Settings.recreateMain = false
            recreate(); return; }
    }

    /*override fun onInitializationComplete(adsInitStatus: InitializationStatus) {
        super.onInitializationComplete(adsInitStatus)
        if (!adsInitStatus.isReady() || !::b.isInitialized) return
        adBanner = UiTools.adaptiveBanner(this, R.string.bnrBtmDrawer)
        b.nav.addView(adBanner, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })
    }*/

    override fun onNavigationItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.mnDownloads -> goTo(Downloads::class)
        R.id.mnFavourites -> goTo(Favourites::class)
        R.id.mnMassFollower -> goTo(MassFollower::class)
        R.id.mnGSettings -> goTo(Settings::class)
        R.id.mnSettings -> goTo(Settings::class) { putExtra(Settings.EXTRA_IS_GLOBAL, false) }
        R.id.mnSwitchAccount -> if (ForegroundService.anyRunning()) {
            AlertDialog.Builder(
                ContextThemeWrapper(this, R.style.Theme_InstaTools_Dialog_Tertiary)
            ).apply {
                setTitle(R.string.backgroundTasks)
                setMessage(R.string.terminateBgTasks)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    ForegroundService.terminateTasks(c)
                    switchAcc()
                }
            }.show()
            true
        } else {
            switchAcc(); true; }
        R.id.mnSignOut -> {
            val bd = AlsoDeleteDataBinding.inflate(
                layoutInflater.cloneInContext(wrapTheme(Theme.TERTIARY))
            )
            AlertDialog.Builder(
                ContextThemeWrapper(this, R.style.Theme_InstaTools_Dialog_Tertiary)
            ).apply {
                setTitle(R.string.signOut)
                setMessage(R.string.signOutSure)
                setView(bd.root)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    if (m.acc == null) return@setPositiveButton
                    Api<Rest.Signing>(
                        this@Main, Api.Endpoint.SIGN_OUT.url, Rest.Signing::class, null,
                        method = Request.Method.POST,
                        body = "one_tap_app_login=1&user_id=${m.acc?.id}",
                        onError = { signOut(bd.root.isChecked) }
                    ) { signOut(bd.root.isChecked) }
                }
            }.show(); true; }
        R.id.mnSupport -> {
            val bs = SupportMethodsBinding.inflate(layoutInflater)
            bs.instagram.setOnClickListener { UiTools.openProfile(this@Main, UiTools.OUR_IG) }
            bs.googlePlay.setOnClickListener {
                if (!UiTools.hasReviewedApp(this@Main)) UiTools.reviewApp(this@Main)
                else UiTools.openLink(
                    this@Main, "https://play.google.com/store/apps/details" +
                            "?id=ir.mahdiparastesh.instatools.beth"
                )
            }
            if (Build.BRAND != "samsung") bs.galaxyStore.vis(false) else {
                Glide.with(c).load("https://galaxystore.samsung.com/galaxyapps.png")
                    .into(bs.galaxyStoreIv)
                bs.galaxyStore.setOnClickListener {
                    UiTools.openLink(
                        this@Main, "https://galaxystore.samsung.com/detail/" +
                                "ir.mahdiparastesh.instatools.beth"
                    )
                }
            }
            mm.showingHelp = true
            AlertDialog.Builder(
                ContextThemeWrapper(this, R.style.Theme_InstaTools_Dialog_Secondary)
            ).apply {
                setTitle(R.string.stHelp)
                setMessage(R.string.supportDesc)
                setView(bs.root)
                setPositiveButton(R.string.ok, null)
                setOnDismissListener { mm.showingHelp = false }
            }.show(); true; }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        (b.toolbar.menu.findItem(R.id.mtSearch)?.actionView as SearchView?)?.apply {
            searchInput = findViewById(androidx.appcompat.R.id.search_src_text)
            // useless: search_button, search_go_btn, search_mag_icon
            searchClose = findViewById(androidx.appcompat.R.id.search_close_btn)
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
                    schQueue.cancelAll { true }
                    schQueue.adder = Api<Rest.Search>(
                        this@Main, Api.Endpoint.SEARCH.url.format(newText), Rest.Search::class,
                        null, cache = true, autoQueue = false, onError = {
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
            colorAc.value?.also {
                val cf = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
                searchInput?.setTextColor(it)
                searchInput?.setHintTextColor(weaken(it))
                searchClose?.colorFilter = cf
            }
        }
        b.toolbar.menu.findItem(R.id.mtSearch)?.setOnActionExpandListener(object :
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
        sp?.edit { putInt(spMainPage, mm.currentPage.value!!) }
        b.toolbar.popupTheme = popupThemes[i]

        anTheme?.cancel()
        val col = if (night()) bg else ca
        anTheme = ValueAnimator.ofArgb(col[lastPage], col[mm.currentPage.value!!]).apply {
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
        maxCharacterCount = UiTools.MAX_BADGE_CHAR
    }

    override fun selective(bb: Boolean): Boolean {
        if (!super.selective(bb)) return false
        b.bnv.menu.forEach { it.isEnabled = !bb }
        toggleNav.isDrawerIndicatorEnabled = !bb
        return true
    }

    private fun signOut(bd: Boolean) {
        ForegroundService.terminateTasks(c)
        CoroutineScope(Dispatchers.IO).launch {
            if (bd) m.acc?.also { acc ->
                Settings.deleteDb(acc.id.toString())
                Settings.deleteSp(this@Main, acc)
            }
            Account.save(c, Account.load(c).apply { removeAll { it.id == m.acc?.id } })
            withContext(Dispatchers.Main) { switchAcc() }
        }
    }

    override fun switchAcc() {
        page1?.thread?.interrupt()
        page2?.thread?.interrupt()
        page2?.saver?.interrupt()
        page3?.boxThread?.interrupt()
        page3?.thdThread?.interrupt()
        mm.accountSwitched()
        super.switchAcc()
    }

    private var exiting = false

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (::b.isInitialized && b.root.isDrawerOpen(GravityCompat.START)) {
            b.root.closeDrawer(GravityCompat.START)
            toggleNav.syncState()
            return; }
        if (pageGoBack()) return
        if (!exiting) {
            exiting = true
            Delay(4000L) { exiting = false }
            Toast.makeText(c, R.string.toExit, Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch { clearCacheIfNecessary() }
            return; }
        super.onBackPressed() // Do NOT kill the process
    }


    class MainNormal : Main()

    class TmCensored : Main()

    class Switcher : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            packageManager.setComponentEnabledSetting(
                ComponentName(packageName, "${javaClass.`package`!!.name}.Main\$MainNormal"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
            startActivity(Intent(this, Main::class.java))
            finish()
        }
    }

    /* TODO:
    * Problems:
    * Volley throws OutOfMemory as soon as downloading long videos start!
    * When you navigate to PageSvd and then come back to PageBox, ListThd doesn't show Expandable
    * Only when you switch to the night mode, the PageSvd overflow menu has the same colour of that theme
    *
    * Extension:
    * "Automatic Actions": Live Unfollower finder, notification-enabled download
    * Conditional jump to bottom
    * Exporter maximum date of top and bottom which would need a calendar picker!?!?
    * Live Live downloader
    * Undo for Unsave
    * View post/story in Instatools intent filter
    * Max slides for HtmlExporter
    */
}
