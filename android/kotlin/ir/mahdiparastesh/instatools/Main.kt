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
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ir.mahdiparastesh.instatools.Settings.Companion.spMainPage
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.FeedTray
import ir.mahdiparastesh.instatools.api.GraphQlQuery
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.base.ForegroundService
import ir.mahdiparastesh.instatools.base.MultiPagedActivity
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.databinding.AlsoDeleteDataBinding
import ir.mahdiparastesh.instatools.databinding.ListDrawerNavBinding
import ir.mahdiparastesh.instatools.databinding.MainBinding
import ir.mahdiparastesh.instatools.frag.PageFav
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageTry
import ir.mahdiparastesh.instatools.list.ListSch
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.NavItem
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main Activity of this application which contains 3 Fragments:
 *
 * | Fragment  | Displaying:           | Theme colour |
 * |-----------|-----------------------|--------------|
 * | [PageFav] | Favourite IG accounts | Yellow       |
 * | [PageSvd] | Saved posts & reels   | Pink         |
 * | [PageTry] | Story tray of feed    | Blue         |
 *
 * [Main] changes its own theme colours based on its page changes;
 * for example its background colour changes to blue in night mode when you switch to [PageTry].
 */
class Main : MultiPagedActivity(PageFav::class, PageSvd::class, PageTry::class),
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
    /*private val popupThemes = arrayOf(
        R.style.Widget_InstaTools_Popup_Primary,
        R.style.Widget_InstaTools_Popup_Secondary,
        R.style.Widget_InstaTools_Popup_Tertiary
    )*/

    override val menuRes = R.menu.main_tlb
    override var currentPage: Int
        get() = vm.currentPage
        set(i) {
            vm.currentPage = i
        }

    class MyModel : ViewModel() {
        var currentPage = Settings.defSpMainPage

        // pages
        var favourites: List<Favourite> = listOf()
        var saved: Rest.LazyList<Media.Wrapper>? = null
        val savedCount = MutableLiveData<Int?>(null)
        var tray: FeedTray? = null
        //val favCount = MutableLiveData<Int>(0)
        //val trayCount = MutableLiveData<Int?>(null)

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
        b.toFavourites.setOnClickListener { turnToPage(0) }
        b.toSaved.setOnClickListener { turnToPage(1) }
        b.toTray.setOnClickListener { turnToPage(2) }
        /*vm.favCount.observe(this) {
            b.toFavourites.text = getString(R.string.favourites) + (if (it > 0) " {$it}" else "")
        }*/
        vm.savedCount.observe(this) {
            b.toSaved.text = getString(R.string.saved) + (if (it != null && it > 0) " {${
                UiTools.displayLargeNumber(it.toLong(), c)
            }}" else "")
        }
        /*vm.trayCount.observe(this) {
            b.toTray.text = getString(R.string.tray) + (if (it != null && it > 0) " {$it}" else "")
        }*/

        // theming
        applyPageStyles(vm.currentPage)
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
            (currentPage() as? PageSvd)
                ?.apply { if (isBInitialised()) b.expanded.root.setBackgroundColor(it) }
        } else colorAc.observe(this) {
            if (it == null) return@observe
            styliseToolbar()
        }
        if (night) colorBG.value = bg[vm.currentPage]
        else colorAc.value = ca[vm.currentPage]

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
                        signOut(bd.tick.isChecked)
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
            searchView = SearchView(wrapTheme(currentTheme()))
            actionView = searchView
            searchView.queryHint = getString(R.string.mtSearch)
            setOnActionExpandListener(this@Main)

            // after a configuration change
            vm.schQuery?.also {
                b.searchRes.adapter = ListSch(this@Main)
                expandActionView()
                searchView.setQuery(vm.schQuery, false)
            }

            // apply styles
            try {
                val searchET = (((searchView
                    .getChildAt(0) as LinearLayout)
                    .getChildAt(2) as LinearLayout)
                    .getChildAt(1) as LinearLayout)
                    .getChildAt(0) as TextView
                searchET.setTextAppearance(R.style.TextAppearance_InstaTools_Search)
            } catch (_: IndexOutOfBoundsException) {
            } catch (_: ClassCastException) {
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

    @SuppressLint("NotifyDataSetChanged")
    override fun onQueryTextSubmit(query: String): Boolean {
        vm.schQuery = query

        if (vm.schErrored) b.searchStatus.setAnimation(R.raw.pending)
        vm.schErrored = false
        b.searchStatus.playAnimation()
        b.searchStatus.vis()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val results = Api.json<GraphQl>(
                    Api.Endpoint.QUERY.url, true, GraphQlQuery.SEARCH.body(query)
                ).data!!.xdt_api__v1__fbsearch__topsearch_connection!!
                withContext(Dispatchers.Main) {
                    b.searchStatus.vis(false)
                    b.searchStatus.pauseAnimation()
                    vm.schRes = results.users.sortedBy { it.position }
                    b.searchRes.adapter?.notifyDataSetChanged()
                }
            } catch (e: Api.FailureException) {
                withContext(Dispatchers.Main) {
                    //UiTools.snackbar(b.root, UiTools.apiError(c, e.code))
                    Toast.makeText(c, UiTools.apiError(c, e.code), Toast.LENGTH_LONG).show()
                    vm.schErrored = true
                    b.searchStatus.setAnimation(R.raw.failed)
                }
            }
        }
        return true
    }

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
        applyPageStyles(i)

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

    private fun applyPageStyles(i: Int) {
        b.bnv.forEachIndexed { vi, v ->
            v.isSelected = vi == i
            (v as TextView).setTextAppearance(
                if (vi == i) R.style.TextAppearance_InstaTools_BNV_Active
                else R.style.TextAppearance_InstaTools_BNV_Inactive
            )
        }
        //b.toolbar.popupTheme = popupThemes[i]
    }

    override fun selective(
        bb: Boolean,
        selectiveToolbarMenuRes: Int?,
        selectiveToolbarListener: Toolbar.OnMenuItemClickListener?
    ): Boolean {
        if (!super.selective(bb, selectiveToolbarMenuRes, selectiveToolbarListener)) return false
        styliseToolbar()
        b.bnv.forEach { it.isClickable = !bb }
        drawerToggle.isDrawerIndicatorEnabled = !bb
        if (bb) b.selectionCount.setTextColor(ca[vm.currentPage])
        return true
    }

    private fun signOut(bd: Boolean) {
        ForegroundService.terminateTasks(c)
        CoroutineScope(Dispatchers.IO).launch {
            if (bd && c.acc != null) {
                c.storageManager.deletePickles()
                c.storageManager.deleteSp()
            }
            Account.save(c, Account.load(c).apply { removeAll { it.id == c.acc?.id } })
            withContext(Dispatchers.Main) { switchAcc() }
        }
    }

    private fun switchAcc() {
        goTo(Login::class, true)
        c.onLoggedOut()
    }

    @SuppressLint("WrongConstant")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
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
            return; }

        @Suppress("DEPRECATION")
        super.onBackPressed()  // do NOT kill the process
    }
}
