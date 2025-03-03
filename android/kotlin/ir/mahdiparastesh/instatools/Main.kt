package ir.mahdiparastesh.instatools

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.forEach
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import ir.mahdiparastesh.instatools.Settings.Companion.spMainPage
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.DownloadHistory
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.databinding.MainNavHeaderBinding
import ir.mahdiparastesh.instatools.frag.PageFav
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.list.ListSch
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.view.MultiPagedActivity
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Main : MultiPagedActivity(PageFav::class, PageSvd::class),
    NavigationView.OnNavigationItemSelectedListener {
    lateinit var b: MainBinding
    val mm: MyModel by viewModels()
    private lateinit var toggleNav: ActionBarDrawerToggle
    private lateinit var bh: MainNavHeaderBinding
    private var exiting = false

    // themes
    private var anTheme: ValueAnimator? = null
    val bg: IntArray by lazy { resources.getIntArray(R.array.BG) }
    val ca: IntArray by lazy { resources.getIntArray(R.array.CA) }
    private val colorBG = MutableLiveData<Int?>(null)
    private val bnvButtons = arrayOf(R.id.to_favourites, R.id.to_saved) // R.id.to_direct
    private val popupThemes = arrayOf(
        R.style.Theme_InstaTools_Popup_Primary,
        R.style.Theme_InstaTools_Popup_Secondary,
        R.style.Theme_InstaTools_Popup_Tertiary
    )

    // search
    @SuppressLint("RestrictedApi")
    lateinit var searchInput: SearchView.SearchAutoComplete
    private lateinit var searchClose: ImageView
    var schRes: Array<Rest.ItemUser>? = null
    var searchErrored = false

    override val menuRes = R.menu.main_tlb
    override val com: ActivityCompanion get() = Companion
    override val currentPage: MutableLiveData<Int> get() = mm.currentPage
    override fun defPage(): Int = intent.extras?.getInt(EXTRA_TURN_TO_PAGE)
        ?: c.sp?.getInt(spMainPage, Settings.defSpMainPage)
        ?: Settings.defSpMainPage

    class MyModel : ViewModel() {
        var saved: Rest.LazyList<Rest.SavedItem>? = null
        val savedCount = MutableLiveData<Int?>(null)
        val currentPage = MutableLiveData(Settings.defSpMainPage)
    }

    companion object : ActivityCompanion()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!c.gsp.contains(Login.SP_ACCOUNT)) {
            goTo(Login::class, true); return; }
        b = MainBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.app_name, getString(R.string.app_name))
        createPages()

        // bottom navigation bar
        b.bnv.itemIconTintList = null // It seems impossible to do this via XML.
        b.bnv.setOnItemSelectedListener { turnToPage(bnvButtons.indexOf(it.itemId)) }
        mm.savedCount.observe(this) { bnvBadge(1, it) }
        b.bnv.selectedItemId = bnvButtons[mm.currentPage.value!!]

        // theming
        if (night()) colorBG.observe(this) {
            if (it == null) return@observe
            window.decorView.setBackgroundColor(it)
            @Suppress("DEPRECATION")
            window.statusBarColor = it // FIXME won't affect Android 15+
            @Suppress("DEPRECATION")
            window.navigationBarColor = it // FIX ME won't affect Android 15+
            styliseToolbar()
            onPrepareOptionsMenu(b.toolbar.menu)
            b.nav.setBackgroundColor(it)
            b.searchRes.setBackgroundColor(it)
            b.bnv.setBackgroundColor(it)
            if (pages[1]?.isBInitialised() == true)
                (pages[1] as? PageSvd)?.b?.expanded?.root?.setBackgroundColor(it)
        } else colorAc.observe(this) {
            if (it == null) return@observe
            styliseToolbar()
            if (::searchInput.isInitialized) {
                searchInput.setTextColor(it)
                searchInput.setHintTextColor(weaken(it))
            }
            if (::searchClose.isInitialized)
                searchClose.colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
        }
        if (night()) colorBG.value = bg[mm.currentPage.value!!]
        else colorAc.value = ca[mm.currentPage.value!!]
        b.toolbar.popupTheme = popupThemes[mm.currentPage.value!!]
        UiTools.bnvTitles(b.bnv).forEachIndexed { i, it -> it.setTextColor(ca[i / 2]) }

        // navigation
        toggleNav = ActionBarDrawerToggle(
            this, b.root, b.toolbar, R.string.navOpen, R.string.navClose
        ).apply {
            b.root.addDrawerListener(this)
            isDrawerIndicatorEnabled = true
            syncState()
        }
        b.nav.setNavigationItemSelectedListener(this)
        bh = MainNavHeaderBinding.bind(b.nav.getHeaderView(0) as ConstraintLayout)
        Glide.with(c)
            .load(c.acc!!.pict)
            .placeholder(drawable(R.drawable.transparent_square))
            .into(bh.pict)
        bh.user.text = c.acc!!.user
        if (!c.acc!!.name.isNullOrBlank())
            bh.name.text = c.acc!!.name
        else bh.name.vis(false)
        bh.ll.setOnClickListener {
            //UiTools.openLink(this, UiTools.PROFILE.format(c.acc!!.user!!))
            (supportFragmentManager.findFragmentByTag("Hey") as? PageSvd)?.b?.rv?.vis(false)
        }

        // permissions
        if (UiTools.reqPermissions.isNotEmpty())
            ActivityCompat.requestPermissions(this, UiTools.reqPermissions, 0)

        // miscellaneous
        if (c.downloadHistory == null) DownloadHistory.load(c)
        CoroutineScope(Dispatchers.IO).launch {
            c.fav = ArrayList(c.dao.favourites())
            c.fav?.sortBy { it.user }
        }
        if (c.gsp.getInt(
                Settings.spUsedVersion, BuildConfig.VERSION_CODE
            ) != BuildConfig.VERSION_CODE
            || !c.gsp.contains(Settings.spUsedVersion)
        ) c.gsp.edit { putInt(Settings.spUsedVersion, BuildConfig.VERSION_CODE) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.extras?.getInt(EXTRA_TURN_TO_PAGE)
            ?.also { if (::b.isInitialized) turnToPage(it) }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.recreateMain) {
            Settings.recreateMain = false
            recreate(); return; }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.mnDownloads -> goTo(Downloads::class)
        R.id.mnGSettings -> goTo(Settings::class)
        R.id.mnSettings -> goTo(Settings::class) { putExtra(Settings.EXTRA_IS_GLOBAL, false) }
        R.id.mnSwitchAccount -> if (ForegroundService.anyRunning()) {
            MaterialAlertDialogBuilder(
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
            MaterialAlertDialogBuilder(
                ContextThemeWrapper(this, R.style.Theme_InstaTools_Dialog_Tertiary)
            ).apply {
                setTitle(R.string.signOut)
                setMessage(R.string.signOutSure)
                setView(bd.root)
                setNegativeButton(R.string.no, null)
                setPositiveButton(R.string.yes) { _, _ ->
                    if (c.acc == null) return@setPositiveButton
                    CoroutineScope(Dispatchers.IO).launch {
                        Api.json<Rest.QuickResponse>(
                            Api.Endpoint.LOGOUT.url,
                            true, "one_tap_app_login=1&user_id=${c.acc?.id}",
                        )
                    }
                    signOut(bd.root.isChecked)
                }
            }.show(); true; }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        (b.toolbar.menu.findItem(R.id.mtSearch)?.actionView as SearchView?)?.apply {
            searchInput = findViewById(androidx.appcompat.R.id.search_src_text)
            // useless: search_button, search_go_btn, search_mag_icon
            searchClose = findViewById(androidx.appcompat.R.id.search_close_btn)
            searchInput.setHint(R.string.mtSearch)
            searchInput.textSize = dimen(R.dimen.searchFont)

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
                    UiTools.accFromUrl.forEach { host ->
                        UiTools.accountFromUrl(newText, host)?.also {
                            searchInput.setText(it)
                            return true // onQueryTextChange will be invoked again by setText!
                        }
                    }
                    if (newText.startsWith("@")) {
                        searchInput.setText(newText.substring(1)); return true; }

                    if (searchErrored) b.searchStatus.setAnimation(R.raw.pending)
                    searchErrored = false
                    b.searchStatus.playAnimation()
                    b.searchStatus.vis()

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val rest =
                                Api.json<Rest.Search>(Api.Endpoint.SEARCH.url.format(newText))
                            withContext(Dispatchers.Main) {
                                b.searchStatus.vis(false)
                                b.searchStatus.pauseAnimation()
                                schRes = rest.users.sortedBy { it.position }.toTypedArray()
                                b.searchRes.adapter?.notifyDataSetChanged()
                            }
                        } catch (_: Api.FailureException) {
                            withContext(Dispatchers.Main) {
                                searchErrored = true
                                b.searchStatus.setAnimation(R.raw.failed)
                            }
                        }
                    }
                    return true
                }
            })
            colorAc.value?.also {
                val cf = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
                searchInput.setTextColor(it)
                searchInput.setHintTextColor(weaken(it))
                searchClose.colorFilter = cf
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
        c.sp?.edit { putInt(spMainPage, mm.currentPage.value!!) }
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

    @Suppress("SameParameterValue")
    private fun bnvBadge(i: Int, num: Int?) = b.bnv.getOrCreateBadge(bnvButtons[i]).apply {
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
            if (bd) c.acc?.also { acc ->
                Settings.deleteDb(acc.id.toString())
                Settings.deleteSp(this@Main, acc)
            }
            Account.save(c, Account.load(c).apply { removeAll { it.id == c.acc?.id } })
            withContext(Dispatchers.Main) { switchAcc() }
        }
    }

    private fun switchAcc() {
        goTo(Login::class, true)
        c.onLoggedOut()
    }

    @Suppress("OVERRIDE_DEPRECATION")
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
            CoroutineScope(Dispatchers.IO).launch { c.clearCacheIfNecessary() }
            return; }
        @Suppress("DEPRECATION") super.onBackPressed() // Do NOT kill the process
    }
}
