package ir.mahdiparastesh.instatools

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.appcompat.widget.Toolbar
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.GraphQl.Page
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Story
import ir.mahdiparastesh.instatools.api.User
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.frag.PageSto
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.job.SimpleJobs
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.util.BasePageViewer
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.TriplePageActivity
import ir.mahdiparastesh.instatools.view.UiTools
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
            for (host in UiTools.accFromUrl)
                UiTools.accountFromUrl(data.toString(), host)
                    ?.also { u -> if (userName == null) userName = u }
            if (userName == null) {
                Toast.makeText(c, R.string.vwLinkNotProfile, Toast.LENGTH_LONG).show()
                onBackPressed()
                return@also; }
            load(userName = userName)
            return true
        }
        return false
    }

    @MainThread
    fun load(userId: String? = null, userName: String? = null, reset: Boolean = false) {
        mm.user?.also { u ->
            if (!reset && (userId == u.id || userName == u.username)) return@load
        }
        if (::b.isInitialized && expandable.zoomed) expandable.collapse()

        loader?.cancel()
        loader = CoroutineScope(Dispatchers.IO).launch {
            var userId_: String? = if (!reset) userId else mm.user!!.id()
            var userName_: String? = userName
            var userReplaced = false

            var pickle: Pickle? = null
            if (userId_ != null) { // if the parameter `userId` is used
                mm.user?.also { oldUser ->
                    userReplaced = oldUser.id!! != userId_
                }
                pickle = Pickle(c, Pickle.Type.PROFILE, userId_)
                val cache = if (!userReplaced && !reset) pickle.restore<Array<User>>() else null
                if (cache != null && cache.size == 2) {
                    mm.user = cache[0]
                    mm.profile = cache[1]
                } else {
                    mm.user = try {
                        SimpleJobs.userInfo(userId_)
                    } catch (e: Api.FailureException) {
                        onError(e.code)
                        loader = null
                        return@launch
                    }
                    userName_ = mm.user!!.username!!

                    mm.profile = try {
                        SimpleJobs.profileInfo(userName_)
                    } catch (e: Api.FailureException) {
                        onError(e.code)
                        loader = null
                        return@launch
                    }
                }

            } else { // if the parameter `userName` is used
                mm.user?.also { oldUser ->
                    userReplaced = oldUser.username!! != userName_
                }
                mm.profile = try {
                    SimpleJobs.profileInfo(userName_!!)
                } catch (e: Api.FailureException) {
                    onError(e.code)
                    loader = null
                    return@launch
                }
                userId_ = mm.profile!!.id!!
                if (mm.user == null) mm.user = try {
                    SimpleJobs.userInfo(userId_)
                } catch (e: Api.FailureException) {
                    onError(e.code)
                    loader = null
                    return@launch
                }
            }

            if (pickle == null) pickle = Pickle(c, Pickle.Type.PROFILE, userId_)
            pickle.save(arrayOf(mm.user!!, mm.profile!!))

            if (userReplaced) {
                mm.posts = null
                mm.story = null
                mm.highlights = null
                mm.tagged = null
            } else
                mm.fav = dao.favourite(mm.user!!.id!!)

            withContext(Dispatchers.Main) {
                if (userReplaced)
                    pages().forEach { (it as BasePageViewer?)?.clear() }
                page2?.showProfile()
                b.toolbar.title = mm.user?.username
                fixTbMenu()
                loader = null
            }
        }
    }

    private suspend fun onError(code: Int) {
        withContext(Dispatchers.Main) {
            if (mm.user != null) {
                page2?.b?.refresher?.isRefreshing = false // in case of a refresh
                UiTools.snackbar(b.root, UiTools.apiError(c, code))
            } else {
                Toast.makeText(c, UiTools.apiError(c, code), Toast.LENGTH_LONG).show()
                @Suppress("DEPRECATION") super.onBackPressed()
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtInsta -> mm.user?.username?.also { UiTools.openProfile(this, it) }
            R.id.vtFav -> mm.user?.also { u ->
                CoroutineScope(Dispatchers.IO).launch {
                    if (mm.fav == null) {
                        mm.fav = Favourite(u.id(), u.username!!, u.full_name!!, u.picture(), u.pv())
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
                        setShortLabel(u.full_name!!.ifBlank { u.username!! })
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
