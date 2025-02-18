package ir.mahdiparastesh.instatools

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.annotation.MainThread
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
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.api.User
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.frag.PageSto
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Viewer : TriplePageActivity<PageSto, PageVwr, PageTag>(), Toolbar.OnMenuItemClickListener {
    lateinit var b: ViewerBinding
    val mm: MyModel by viewModels()
    private var loader: Job? = null
    val expandable: Expandable by lazy {
        Expandable(
            this, b.expanded, color(if (!night()) R.color.defBG else R.color.CS)
        ) { (pages()[currentPage.value!!] as BasePageViewer).updateShadow() }
    }

    override val menuRes = R.menu.viewer_tlb
    override val com: ActivityCompanion get() = Companion
    override val currentPage: MutableLiveData<Int> get() = mm.currentPage
    override val aKlass = PageSto::class
    override val bKlass = PageVwr::class
    override val cKlass = PageTag::class
    override val mode = TripleMode.FRAGMENT_MANAGER
    override fun defPage(): Int = 1

    class MyModel : ViewModel() {
        var user: User? = null
        var profile: User? = null
        var posts: Page<Media>? = null
        var story: Story? = null
        var highlights: Page<Story>? = null
        var tagged: Page<Media>? = null
        var currentPage = MutableLiveData(1)
        var fav: Favourite? = null

        fun reset() {
            user = null
            profile = null
            posts = null
            story = null
            highlights = null
            tagged = null
            currentPage.value = 1
            fav = null
        }
    }

    companion object : ActivityCompanion() {
        private const val EXTRA_USER_ID = "userId"

        fun comeHere(c: BaseActivity, userId: String) {
            c.goTo(Viewer::class) { putExtra(EXTRA_USER_ID, userId) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resolvedIntent == false) return
        b = ViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.vwTitle)
        createPages(toDefaultPage = false)

        b.refresher.setOnRefreshListener {
            load(refresh = true)
        }
        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback (pages()[mm.currentPage.value!!] as BasePageViewer?)
                ?.avoidRefresh() == true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        fixTbMenu()
        return true
    }

    @MainThread
    fun fixTbMenu() {
        b.toolbar.menu.findItem(R.id.vtFav)
            ?.setIcon(if (mm.fav != null) R.drawable.favourite else R.drawable.non_favourite)
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.extras?.getString(EXTRA_USER_ID)?.also { userId ->
            load(userId = userId)
            return true
        }
        intent.data?.also { data ->
            var userName: String? = null
            for (host in UiTools.ACC_FROM_URL)
                data.toString().accFromUrl(host)
                    ?.also { u -> if (userName == null) userName = u }
            if (userName == null) return@also
            load(userName = userName)
            return true
        }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.also { mediaLink ->
            load(mediaLink = mediaLink)
            return true
        }
        return false
    }

    @MainThread
    private fun load(
        userId: String? = null,
        userName: String? = null,
        mediaLink: String? = null,
        refresh: Boolean = false
    ) {
        mm.user?.also { u ->
            if (!refresh && (userId == u.id || userName == u.username)) return@load
        }
        if (expandable.zoomed) expandable.collapse()

        loader?.cancel()
        loader = CoroutineScope(Dispatchers.IO).launch {
            var userId_: String? = userId
            var userName_: String? = userName
            var userReplaced = false

            // get user name via media link if shared
            if (mediaLink != null) {
                val html = Api.page(mediaLink) { code ->
                    b.refresher.isRefreshing = false
                    snackbar(b.root, Api.error(code), Snackbar.LENGTH_LONG)
                }
                if (html == null) { // got an API error
                    loader = null
                    return@launch; }

                userName_ = html
                    .substringAfter("<meta property=\"instapp:owner_user_id\" content=\"")
                    .substringBefore("\"")
                mm.user?.also { u ->
                    if (!refresh && userName_ == u.username) {
                        loader = null
                        return@launch; }
                    userReplaced = true
                }
            }

            if (userId_ == null) {
                mm.profile = userProfile(userName_!!)
                if (mm.profile == null) { // got an API error
                    loader = null
                    return@launch; }
                userId_ = mm.profile!!.id!!
                mm.user?.also { u ->
                    if (!refresh && userId_ == u.id) {
                        loader = null
                        return@launch; }
                    userReplaced = true
                }
            } else {
                mm.user = userInfo(userId_)
                if (mm.user == null) { // got an API error
                    loader = null
                    return@launch; }
                userName_ = mm.user!!.username!!
                mm.user?.also { u ->
                    if (!refresh && userName_ == u.username) {
                        loader = null
                        return@launch; }
                    userReplaced = true
                }
            }

            mm.profile = userProfile(userName_)
            if (mm.profile == null) {
                loader = null
                return@launch; }

            if (userReplaced) {
                mm.posts = null
                mm.story = null
                mm.highlights = null
                mm.tagged = null
            } else
                mm.fav = dao.favourite(mm.user!!.id!!)

            withContext(Dispatchers.Main) {
                page2?.showProfile()
                if (userReplaced)
                    pages().forEach { (it as BasePageViewer?)?.reset() }
                b.toolbar.title = mm.user?.username
                b.refresher.isRefreshing = false
                fixTbMenu()
                loader = null
            }
        }
    }

    suspend fun userInfo(userId: String): User? =
        Api.call<Rest.UserInfo>(
            Api.Endpoint.INFO.url.format(userId), Rest.UserInfo::class,
            onError = { code ->
                b.refresher.isRefreshing = false
                snackbar(b.root, Api.error(code), Snackbar.LENGTH_LONG)
            }
        )?.user

    suspend fun userProfile(userName: String): User? =
        Api.call<GraphQl>(
            Api.Endpoint.PROFILE.url.format(userName), GraphQl::class,
            onError = { code ->
                b.refresher.isRefreshing = false
                snackbar(b.root, Api.error(code), Snackbar.LENGTH_LONG)
            }
        )?.data?.user

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtInsta -> mm.user?.username?.also { UiTools.openProfile(this, it) }
            R.id.vtFav -> mm.user?.also { u ->
                CoroutineScope(Dispatchers.IO).launch {
                    if (mm.fav == null) {
                        mm.fav = u.favourite()
                        dao.addFavourite(mm.fav!!)
                        m.fav?.add(mm.fav!!)
                    } else {
                        dao.deleteFavourite(mm.fav!!)
                        m.fav?.remove(mm.fav!!)
                        mm.fav = null
                    }
                    withContext(Dispatchers.Main) { fixTbMenu() }
                }
            }
            R.id.vtShortcut -> mm.user?.also { u ->
                val bmp = (page2?.b?.proPicIv?.drawable as BitmapDrawable?)?.bitmap ?: return@also
                ShortcutManagerCompat.requestPinShortcut(
                    c, ShortcutInfoCompat.Builder(c, u.username!!).apply {
                        setIntent(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(UiTools.PROFILE.format(u.username))
                            ).setPackage(UiTools.INSTA_PACKAGE)
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
        if (mm.currentPage.value != 1) {
            turnToPage(1); return; }
        mm.reset()
        @Suppress("DEPRECATION") super.onBackPressed()
    }
}
