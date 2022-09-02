package ir.mahdiparastesh.instatools

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media2.common.SessionPlayer
import com.android.volley.NetworkResponse
import com.android.volley.toolbox.Volley
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.json.GraphQl
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_ABORTED
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_FETCHED
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.accFromUrl
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

class Viewer : TriplePageActivity<PageRel, PageVwr, PageTag>(), Toolbar.OnMenuItemClickListener {
    lateinit var b: ViewerBinding
    var user: String? = null
    var dbFav: Favourite? = null
    private var thread: Initial? = null
    val reqQueue by lazy { Volley.newRequestQueue(c) }
    val expandable: Expandable by lazy {
        Expandable(
            this, b.expanded, handler, reqQueue, color(if (!night()) R.color.defBG else R.color.CS)
        ) { (pages()[currentPage.value!!] as BasePageViewer).updateShadow() }
    }
    val mm: MyModel by viewModels()

    override val menuRes = R.menu.viewer_tlb
    override val com: ActivityCompanion get() = Companion
    override val currentPage: MutableLiveData<Int> get() = mm.vwCurrentPage
    override val aKlass = PageRel::class
    override val bKlass = PageVwr::class
    override val cKlass = PageTag::class
    override val mode = TripleMode.FRAGMENT_MANAGER
    override fun defPage(): Int = 1

    class MyModel : ViewModel() {
        var vwUser: GraphQl.User? = null
        var vwTagged: Media.MediaWrapperApi? = null
        var vwReels: CopyOnWriteArrayList<Rest.Reel>? = null
        var vwCurrentPage = MutableLiveData(1)
    }

    companion object : ActivityCompanion() {
        private const val EXTRA_USER = "EXTRA_USER"

        fun comeHere(c: BaseActivity, user: String) {
            c.goTo(Viewer::class) { putExtra(EXTRA_USER, user) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resolvedIntent == false) return
        b = ViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.vwTitle, user)
        createPages(toDefaultPage = false)

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_FETCHED -> {
                        b.refresher.isRefreshing = false
                        page1?.load()
                        page2?.showProfile()
                        page3?.load()
                    }
                    HANDLE_ABORTED -> {
                        b.refresher.isRefreshing = false
                        UiTools.snackbar(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG)
                    }
                    Api.HANDLE_ERROR -> {
                        b.refresher.isRefreshing = false
                        UiTools.snackbar(
                            b.root, c.getString(
                                R.string.unknownError,
                                (msg.obj as NetworkResponse?)?.statusCode.toString()
                            ), Snackbar.LENGTH_SHORT
                        )
                    }
                    PageSvd.HANDLE_INIT_QUEUER -> Downloads.initService(this@Viewer)
                    Expandable.HANDLE_EXPANDABLE_ERROR ->
                        UiTools.snackbar(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG)
                }
            }
        }

        b.refresher.setOnRefreshListener {
            reset()
            if (thread?.active != true) thread = Initial().also { it.start() }
        }
        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback (pages()[currentPage.value!!] as BasePageViewer?)?.avoidRefresh()
                ?: false
        }

        load(true)
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.extras?.getString(EXTRA_USER)?.let {
            if (!onCreation && user == it) return false
            if (user != it) mm.vwUser = null
            user = it
        }
        intent.data?.let {
            var newUser: String? = null
            for (host in UiTools.ACC_FROM_URL)
                it.toString().accFromUrl(host)
                    ?.let { u -> if (newUser == null) newUser = u }
            if (newUser == null) return@let
            if (!onCreation && newUser == it.toString()) return false
            user = newUser
        }
        if (!onCreation) {
            load()
            b.toolbar.title = user
            if (page1?.bInitialised == true)
                page1?.rv()?.adapter = null
            if (page2?.bInitialised == true) {
                page2?.proPicIv?.setImageDrawable(null)
                page2?.privateAcc?.vis(false)
            }
            if (page3?.bInitialised == true)
                page3?.rv()?.adapter = null
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        fixTbMenu()
        if (Intelligence.galaxyCensor)
            b.toolbar.menu.findItem(R.id.vtInsta)?.isVisible = false
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtInsta -> user?.also { UiTools.openProfile(this, it) }
            R.id.vtFav -> mm.vwUser?.also { user ->
                CoroutineScope(Dispatchers.IO).launch {
                    if (dbFav == null) {
                        dbFav = user.favourite()
                        dao.addFavourite(dbFav!!)
                        m.fav?.add(dbFav!!)
                    } else {
                        dao.deleteFavourite(dbFav!!)
                        m.fav?.remove(dbFav!!)
                        dbFav = null
                    }
                    withContext(Dispatchers.Main) { fixTbMenu() }
                }
            }
            R.id.vtShortcut -> mm.vwUser?.also { u ->
                val bmp = (page2?.proPicIv?.drawable as BitmapDrawable?)?.bitmap ?: return@also
                ShortcutManagerCompat.requestPinShortcut(
                    c, ShortcutInfoCompat.Builder(c, u.username).apply {
                        setIntent(
                            Intent(Intent.ACTION_VIEW, Uri.parse(UiTools.PROFILE.format(user)))
                                .setPackage(UiTools.INSTA_PACKAGE)
                        )
                        setIcon(
                            IconCompat.createWithBitmap(
                                Bitmap.createScaledBitmap(bmp, 128, 128, true)
                            )
                        )
                        setShortLabel(u.full_name.ifBlank { u.username })
                    }.build(), null
                )
                incrementCounter(Settings.spShortcutCount)
            }
        }
        return super.onMenuItemClick(item)
    }

    fun fixTbMenu() {
        b.toolbar.menu.findItem(R.id.vtFav)
            ?.setIcon(if (dbFav != null) R.drawable.favourite else R.drawable.non_favourite)
    }

    private fun load(firstLoad: Boolean = false) {
        if (mm.vwUser != null) {
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            return; }
        reset(firstLoad)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dbFav = dao.favouriteByUser(user!!).getOrNull(0)
                withContext(Dispatchers.Main) { fixTbMenu() }
            } catch (e: NullPointerException) {
            }
        }
        if (thread?.active != true) thread = Initial().also { it.start() }
    }

    private fun reset(firstLoad: Boolean = false) {
        if (!firstLoad) pages().forEach { (it as BasePageViewer?)?.reset() }
        if (expandable.zoomed) expandable.collapse()
    }

    override fun onPause() {
        super.onPause()
        (b.expanded.slider.adapter as ListCar?)?.players
            ?.forEach { if (it?.playerState == SessionPlayer.PLAYER_STATE_PLAYING) it.pause() }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (::b.isInitialized && expandable.zoomed) {
            expandable.collapse(); return; }
        if (pageGoBack()) return
        if (currentPage.value != 1) {
            turnToPage(1); return; }
        mm.vwUser = null
        mm.vwReels = null
        mm.vwTagged = null
        mm.vwCurrentPage.value = 1
        super.onBackPressed()
    }

    inner class Initial : BaseThread() {
        override fun run() {
            reqQueue.adder = Api<GraphQl>(
                this@Viewer, Api.Endpoint.PROFILE.url.format(user), GraphQl::class,
                handler, autoQueue = false, onError = { interrupt() }
            ) { graphql ->
                mm.vwUser = graphql.data.user
                if (mm.vwUser == null) {
                    Toast.makeText(c, R.string.pageNotExist, Toast.LENGTH_SHORT).show()
                    interrupt(); return@Api
                }
                handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                interrupt()
            }
        }
    }
}
