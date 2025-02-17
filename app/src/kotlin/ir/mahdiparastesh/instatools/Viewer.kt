package ir.mahdiparastesh.instatools

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.api.User
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePageViewer
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.TriplePageActivity
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.accFromUrl
import ir.mahdiparastesh.instatools.view.UiTools.snackbar
import ir.mahdiparastesh.instatools.view.UiTools.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

class Viewer : TriplePageActivity<PageRel, PageVwr, PageTag>(), Toolbar.OnMenuItemClickListener {
    lateinit var b: ViewerBinding
    var user: String? = null
    var dbFav: Favourite? = null
    private var thread: Job? = null
    val expandable: Expandable by lazy {
        Expandable(
            this, b.expanded, color(if (!night()) R.color.defBG else R.color.CS)
        ) { (pages()[currentPage.value!!] as BasePageViewer).updateShadow() }
    }
    val mm: MyModel by viewModels()
    private var findUserById: String? = null
    private var findUserByMediaLink: String? = null

    override val menuRes = R.menu.viewer_tlb
    override val com: ActivityCompanion get() = Companion
    override val currentPage: MutableLiveData<Int> get() = mm.vwCurrentPage
    override val aKlass = PageRel::class
    override val bKlass = PageVwr::class
    override val cKlass = PageTag::class
    override val mode = TripleMode.FRAGMENT_MANAGER
    override fun defPage(): Int = 1

    class MyModel : ViewModel() {
        var vwUser: User? = null
        var vwTagged: Page<Media>? = null
        var vwReels: CopyOnWriteArrayList<Rest.Reel>? = null
        var vwCurrentPage = MutableLiveData(1)
    }

    companion object : ActivityCompanion() {
        private const val EXTRA_USER = "user"
        private const val EXTRA_USER_ID = "userId"

        fun comeHere(c: BaseActivity, user: String) {
            c.goTo(Viewer::class) { putExtra(EXTRA_USER, user) }
        }

        fun comeHereById(c: BaseActivity, userId: String) {
            c.goTo(Viewer::class) { putExtra(EXTRA_USER_ID, userId) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resolvedIntent == false) return
        b = ViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.vwTitle, user)
        createPages(toDefaultPage = false)

        b.refresher.setOnRefreshListener {
            reset()
            if (thread?.isActive != true) initialLoad()
        }
        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback (pages()[currentPage.value!!] as BasePageViewer?)
                ?.avoidRefresh() == true
        }

        if (findUserById == null && findUserByMediaLink == null) load(true)
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.extras?.getString(EXTRA_USER)?.also {
            if (!onCreation && user == it) return false
            if (user != it) mm.vwUser = null
            user = it
            if (!onCreation) gotNewUserName()
            return true
        }
        intent.extras?.getString(EXTRA_USER_ID)?.also {
            findUserById = it
            if (m.acc != null) findUserNameById(it, true) { findUserById = null }
            return true
        }
        intent.data?.also {
            var newUser: String? = null
            for (host in UiTools.ACC_FROM_URL)
                it.toString().accFromUrl(host)
                    ?.let { u -> if (newUser == null) newUser = u }
            if (newUser == null) return@also
            if (!onCreation && newUser == it.toString()) return false
            user = newUser
            if (!onCreation) gotNewUserName()
            return true
        }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.also { link ->
            findUserByMediaLink = link
            if (m.acc != null) findUserNameByMediaLink(onCreation)
            return true
        }
        return false
    }

    override fun onAccountSet() {
        super.onAccountSet()
        when {
            findUserById != null ->
                findUserNameById(findUserById!!, true) { findUserById = null }
            findUserByMediaLink != null -> findUserNameByMediaLink(true)
        }
    }

    private fun findUserNameByMediaLink(onCreation: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val html = Api.page(findUserByMediaLink!!) { code ->
                b.refresher.isRefreshing = false
                snackbar(b.root, Api.error(code), Snackbar.LENGTH_LONG)
            } ?: return@launch

            findUserNameById(
                html.substringAfter("<meta property=\"instapp:owner_user_id\" content=\"")
                    .substringBefore("\""), onCreation
            ) { findUserByMediaLink = null }
        }
    }

    private fun findUserNameById(
        userId: String, onCreation: Boolean, onSuccess: (() -> Unit)? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val info = Api.call<Rest.UserInfo>(
                Api.Endpoint.INFO.url.format(userId), Rest.UserInfo::class, onError = { code ->
                    if (code == 404) notFound()
                    else {
                        b.refresher.isRefreshing = false
                        snackbar(b.root, Api.error(code), Snackbar.LENGTH_LONG)
                    }
                }
            ) ?: return@launch
            user = info.user.username
            try {
                dbFav = dao.favourite(userId)
            } catch (_: NullPointerException) {
            }
            withContext(Dispatchers.Main) {
                fixTbMenu()
                if (onCreation) {
                    load(findFav = false)
                    b.toolbar.title = user
                } else gotNewUserName()
                onSuccess?.also { it() }
            }
        }
    }

    private fun gotNewUserName() {
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        fixTbMenu()
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
                    c, ShortcutInfoCompat.Builder(c, u.username!!).apply {
                        setIntent(
                            Intent(Intent.ACTION_VIEW, Uri.parse(UiTools.PROFILE.format(user)))
                                .setPackage(UiTools.INSTA_PACKAGE)
                        )
                        setIcon(
                            IconCompat.createWithBitmap(
                                Bitmap.createScaledBitmap(bmp, 128, 128, true)
                            )
                        )
                        setShortLabel(u.full_name!!.ifBlank { u.username })
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

    private fun load(firstLoad: Boolean = false, findFav: Boolean = true) {
        if (mm.vwUser != null) {
            loaded()
            return; }
        reset(firstLoad)
        if (findFav) CoroutineScope(Dispatchers.IO).launch {
            try {
                dbFav = dao.favouriteByUser(user!!)
            } catch (_: NullPointerException) {
            }
            withContext(Dispatchers.Main) { fixTbMenu() }
        }
        if (thread?.isActive != true) initialLoad()
    }

    fun initialLoad() {
        thread = CoroutineScope(Dispatchers.IO).launch {
            val graphql = Api.call<GraphQl>(
                Api.Endpoint.PROFILE.url.format(user), GraphQl::class,
                onError = { code ->
                    b.refresher.isRefreshing = false
                    snackbar(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG)
                }
            )
            if (graphql == null) {
                return@launch; }

            mm.vwUser = graphql.data?.user
            if (mm.vwUser == null) {
                notFound()
                return@launch; }
            loaded()
        }
    }

    private fun loaded() {
        b.refresher.isRefreshing = false
        page1?.load()
        page2?.showProfile()
        page3?.load()
    }

    private fun notFound() {
        snackbar(b.root, R.string.pageNotExist, Snackbar.LENGTH_LONG)
    }

    private fun reset(firstLoad: Boolean = false) {
        if (!firstLoad) pages().forEach { (it as BasePageViewer?)?.reset() }
        if (expandable.zoomed) expandable.collapse()
    }

    override fun onPause() {
        super.onPause()
        (b.expanded.slider.adapter as ListCar?)?.sessions
            ?.forEach { if (it?.player?.playbackState == Player.STATE_READY) it.player.pause() }
    }

    @Suppress("OVERRIDE_DEPRECATION")
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
        @Suppress("DEPRECATION") super.onBackPressed()
    }
}
