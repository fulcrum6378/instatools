package ir.mahdiparastesh.instatools

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.forEach
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.Settings.Companion.spMainPage
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.ListDrawerNavBinding
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.frag.PageFav
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.list.ListSch
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.ActionBarDrawerToggle
import ir.mahdiparastesh.instatools.view.MultiPagedActivity
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.NavItem
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Main : MultiPagedActivity(PageFav::class, PageSvd::class),
    MenuItem.OnActionExpandListener, SearchView.OnQueryTextListener {

    lateinit var b: MainBinding
    val vm: MyModel by viewModels()
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var searchView: SearchView
    private val ntfPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var exiting = false

    // theming
    private var anTheme: ValueAnimator? = null
    val bg: IntArray by lazy { resources.getIntArray(R.array.BG) }
    val ca: IntArray by lazy { resources.getIntArray(R.array.CA) }
    private val colorBG = MutableLiveData<Int?>(null)
    private val bnvButtons = arrayOf(R.id.to_favourites, R.id.to_saved)  // R.id.to_direct
    private val popupThemes = arrayOf(
        R.style.Theme_InstaTools_Popup_Primary,
        R.style.Theme_InstaTools_Popup_Secondary,
        R.style.Theme_InstaTools_Popup_Tertiary
    )

    override val menuRes = R.menu.main_tlb
    override var currentPage: Int
        get() = vm.currentPage
        set(i) {
            vm.currentPage = i
        }

    class MyModel : ViewModel() {
        var favourites: List<Favourite> = listOf()
        var saved: Rest.LazyList<Rest.SavedItem>? = null
        val savedCount = MutableLiveData<Int?>(null)
        var currentPage = Settings.defSpMainPage

        // search
        var schQuery: CharSequence? = null
        var schRes: List<GraphQl.SearchResults.ItemUser>? = null
        var schErrored = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!c.gsp.contains(Login.SP_ACCOUNT)) {
            goTo(Login::class, true); return; }
        b = MainBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.app_name, getString(R.string.app_name))

        // bottom navigation bar
        b.bnv.selectedItemId = bnvButtons[vm.currentPage]
        b.bnv.itemIconTintList = null  // It seems impossible to do this via XML.
        b.bnv.setOnItemSelectedListener { turnToPage(bnvButtons.indexOf(it.itemId)) }
        vm.savedCount.observe(this) { bnvBadge(1, it) }
        UiTools.bnvTitles(b.bnv).forEachIndexed { i, it -> it.setTextColor(ca[i / 2]) }

        // theming
        if (night) colorBG.observe(this) {
            if (it == null) return@observe
            window.decorView.setBackgroundColor(it)
            @Suppress("DEPRECATION")  // works fine on Android 15!
            window.statusBarColor = it
            @Suppress("DEPRECATION")  // works fine on Android 15!
            window.navigationBarColor = it
            styliseToolbar()
            onPrepareOptionsMenu(b.toolbar.menu)
            b.drawer.setBackgroundColor(it)
            b.searchRes.setBackgroundColor(it)
            b.bnv.setBackgroundColor(it)
            (currentPage() as? PageSvd)
                ?.apply { if (isBInitialised()) b.expanded.root.setBackgroundColor(it) }
        } else colorAc.observe(this) {
            if (it == null) return@observe
            styliseToolbar()
        }
        if (night) colorBG.value = bg[vm.currentPage]
        else colorAc.value = ca[vm.currentPage]
        b.toolbar.popupTheme = popupThemes[vm.currentPage]

        // drawer
        drawerToggle = ActionBarDrawerToggle(
            this, b.root, b.toolbar, R.string.navOpen, R.string.navClose
        ).apply {
            b.root.addDrawerListener(this)
            isDrawerIndicatorEnabled = true
            syncState()
        }
        Glide.with(c)
            .load(c.acc!!.pict)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(drawable(R.drawable.transparent_square))
            .into(b.drwUserPhoto)
        b.drwUser.text = c.acc!!.user
        if (!c.acc!!.name.isNullOrBlank())
            b.drwUserName.text = c.acc!!.name
        else
            b.drwUserName.vis(false)
        b.drwLL.setOnClickListener {
            UiTools.openLink(this, Utils.PROFILE.format(c.acc!!.user!!))
        }
        val drwNavItems: List<NavItem> = listOf(
            NavItem(
                R.id.mnDownloads, R.drawable.download, R.string.downloads
            ) { goTo(Downloads::class) },

            NavItem(
                R.id.mnGSettings, R.drawable.settings, R.string.gSettings
            ) { goTo(Settings::class) },

            NavItem(
                R.id.mnSettings, R.drawable.settings, R.string.aSettings
            ) { goTo(Settings::class) { putExtra(Settings.EXTRA_IS_GLOBAL, false) } },

            NavItem(R.id.mnSwitchAccount, R.drawable.switch_account, R.string.switchAccount) {
                if (ForegroundService.anyRunning())
                    AlertDialog.Builder(
                        ContextThemeWrapper(this, R.style.Theme_InstaTools_Tertiary)
                    ).apply {
                        setTitle(R.string.backgroundTasks)
                        setMessage(R.string.terminateBgTasks)
                        setNegativeButton(R.string.no, null)
                        setPositiveButton(R.string.yes) { _, _ ->
                            ForegroundService.terminateTasks(c)
                            switchAcc()
                        }
                    }.show()
                else
                    switchAcc()
            },

            NavItem(R.id.mnSignOut, R.drawable.exit, R.string.signOut) {
                val bd = AlsoDeleteDataBinding.inflate(
                    layoutInflater.cloneInContext(wrapTheme(Theme.TERTIARY))
                )
                AlertDialog.Builder(
                    ContextThemeWrapper(this, R.style.Theme_InstaTools_Tertiary)
                ).apply {
                    setTitle(R.string.signOut)
                    setMessage(R.string.signOutSure)
                    setView(bd.root)
                    setNegativeButton(R.string.no, null)
                    setPositiveButton(R.string.yes) { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            Api.json<Rest.QuickResponse>(
                                Api.Endpoint.LOGOUT.url,
                                true, "one_tap_app_login=1&user_id=${c.acc!!.id}",
                            )
                        }
                        signOut(bd.root.isChecked)
                    }
                }.show()
            },
        )
        b.drwNav.adapter = object : ArrayAdapter<NavItem>(this, 0, drwNavItems) {
            override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
                val b = convertView?.let { ListDrawerNavBinding.bind(it) }
                    ?: ListDrawerNavBinding.inflate(layoutInflater)
                b.icon.setImageResource(drwNavItems[i].icon)
                b.text.setText(drwNavItems[i].text)
                b.root.setOnClickListener(drwNavItems[i].listener)
                return b.root
            }
        }

        // request permission for notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) ntfPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        // miscellaneous
        c.downloadHistory.load(c)
        if (c.gsp.getInt(Settings.spUsedVersion, BuildConfig.VERSION_CODE)
            != BuildConfig.VERSION_CODE
            || !c.gsp.contains(Settings.spUsedVersion)
        ) c.gsp.edit { putInt(Settings.spUsedVersion, BuildConfig.VERSION_CODE) }
    }

    override fun setContentView(view: View?) {
        vm.currentPage = intent.extras?.getInt(EXTRA_TURN_TO_PAGE)
            ?: c.sp?.getInt(spMainPage, Settings.defSpMainPage)
                ?: Settings.defSpMainPage
        super.setContentView(view)
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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        b.toolbar.menu.findItem(R.id.mtSearch)?.apply {
            searchView = SearchView(wrapTheme(currentTheme()))  // TODO fucked up
            actionView = searchView
            searchView.queryHint = getString(R.string.mtSearch)
            setOnActionExpandListener(this@Main)

            // after a configuration change
            vm.schQuery?.also {
                b.searchRes.adapter = ListSch(this@Main)
                expandActionView()
                searchView.setQuery(vm.schQuery, false)
            }

            searchView.setOnQueryTextListener(this@Main)
        }
        return true
    }

    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
        b.searchRes.vis()
        if (vm.schQuery == null) vm.schQuery = ""
        return true
    }

    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
        b.searchRes.vis(false)
        b.searchRes.adapter = null
        vm.schQuery = null
        vm.schRes = null
        vm.schErrored = false
        b.searchStatus.vis(false)
        b.searchStatus.pauseAnimation()
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean = true

    @SuppressLint("NotifyDataSetChanged")
    override fun onQueryTextChange(newText: String): Boolean {
        if (newText == "") {
            vm.schRes = null
            if (b.searchRes.adapter == null) b.searchRes.adapter = ListSch(this@Main)
            else b.searchRes.adapter?.notifyDataSetChanged()
            return true
        }
        UiTools.userNameFromUrl(newText)?.also {
            searchView.setQuery(it, true)
            return true // onQueryTextChange will be invoked again by setText!
        }
        if (newText.startsWith("@")) {
            searchView.setQuery(newText.substring(1), true); return true; }
        vm.schQuery = newText

        if (vm.schErrored) b.searchStatus.setAnimation(R.raw.pending)
        vm.schErrored = false
        b.searchStatus.playAnimation()
        b.searchStatus.vis()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val results = Api.json<GraphQl>(
                    Api.Endpoint.QUERY.url, true, GraphQlQuery.SEARCH.body(newText)
                ).data!!.xdt_api__v1__fbsearch__topsearch_connection!!
                withContext(Dispatchers.Main) {
                    b.searchStatus.vis(false)
                    b.searchStatus.pauseAnimation()
                    vm.schRes = results.users.sortedBy { it.position }
                    b.searchRes.adapter?.notifyDataSetChanged()
                }
            } catch (_: Api.FailureException) {
                withContext(Dispatchers.Main) {
                    vm.schErrored = true
                    b.searchStatus.setAnimation(R.raw.failed)
                }
            }
        }
        return true
    }

    private fun currentTheme() = when (vm.currentPage) {
        0 -> Theme.PRIMARY
        1 -> Theme.SECONDARY
        2 -> Theme.TERTIARY
        else -> throw IllegalArgumentException()
    }

    override fun turnToPage(i: Int): Boolean {
        if (!super.turnToPage(i)) return true
        c.sp?.edit { putInt(spMainPage, vm.currentPage) }
        b.toolbar.popupTheme = popupThemes[i]

        anTheme?.cancel()
        val col = if (night) bg else ca
        anTheme = ValueAnimator.ofArgb(col[lastPage], col[vm.currentPage]).apply {
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

    @Suppress("SameParameterValue")
    private fun bnvBadge(i: Int, num: Int?) = b.bnv.getOrCreateBadge(bnvButtons[i]).apply {
        isVisible = num != null
        number = num ?: 0
        backgroundColor = ca[i]
        badgeTextColor = if (!night) bg[i] else color(R.color.defBG)
        maxCharacterCount = UiTools.MAX_BADGE_CHAR
    }

    override fun selective(bb: Boolean): Boolean {
        if (!super.selective(bb)) return false
        b.bnv.menu.forEach { it.isEnabled = !bb }
        drawerToggle.isDrawerIndicatorEnabled = !bb
        return true
    }

    private fun signOut(bd: Boolean) {
        ForegroundService.terminateTasks(c)
        CoroutineScope(Dispatchers.IO).launch {
            if (bd && c.acc != null) {
                c.deletePickles()
                c.deleteSp()
            }
            Account.save(c, Account.load(c).apply { removeAll { it.id == c.acc?.id } })
            withContext(Dispatchers.Main) { switchAcc() }
        }
    }

    private fun switchAcc() {
        goTo(Login::class, true)
        c.onLoggedOut()
    }

    @SuppressLint("RtlHardcoded")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        //val drawerGravity = if (!dirRtl) Gravity.LEFT else Gravity.RIGHT
        if (::b.isInitialized && b.root.isDrawerOpen(GravityCompat.START)) {
            b.root.closeDrawer(GravityCompat.START)
            drawerToggle.syncState()
            return; }

        val searchMenuItem = b.toolbar.menu.findItem(R.id.mtSearch)
        if (searchMenuItem?.isActionViewExpanded == true) {
            searchMenuItem.collapseActionView()
            return; }

        if (pageGoBack()) return

        if (!exiting) {
            exiting = true
            Delay(4000L) { exiting = false }
            Toast.makeText(c, R.string.toExit, Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.IO).launch { c.clearCacheIfNecessary() }
            return; }

        @Suppress("DEPRECATION")
        super.onBackPressed()  // do NOT kill the process
    }
}
