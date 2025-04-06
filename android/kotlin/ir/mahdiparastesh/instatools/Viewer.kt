package ir.mahdiparastesh.instatools

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.viewModels
import androidx.annotation.MainThread
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import androidx.recyclerview.widget.ConcatAdapter
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
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.MultiPagedActivity
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Viewer : MultiPagedActivity(PageSto::class, PageVwr::class, PageTag::class),
    Toolbar.OnMenuItemClickListener {

    lateinit var b: ViewerBinding
    val vm: MyModel by viewModels()
    val expandable: Expandable by lazy {
        Expandable(this, b.expanded, color(if (!night) R.color.defBG else R.color.CS)) {
            (currentPage() as BasePageViewer?)?.apply {
                updateShadow()
                updateJumper()
            }
        }
    }

    override val menuRes = R.menu.viewer_tlb
    override var currentPage: Int
        get() = vm.currentPage
        set(i) {
            vm.currentPage = i
        }

    class MyModel : ViewModel() {
        var user: User? = null
        var profile: User? = null
        var posts: Page<Media>? = null
        var story: Story? = null
        var highlights: Page<Story>? = null
        var tagged: Page<Media>? = null
        var currentPage = 1
        var fav: Favourite? = null
        var reloadUser = false

        fun reset() {
            user = null
            profile = null
            posts = null
            story = null
            highlights = null
            tagged = null
            currentPage = 1
            fav = null
            reloadUser = false
        }
    }

    companion object {
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

        // if the user is already loaded
        vm.user?.also {
            b.toolbar.title = it.username
            fixTbMenu()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        fixTbMenu()
        return true
    }

    @MainThread
    fun fixTbMenu() {
        b.toolbar.menu.findItem(R.id.vtFav)?.setIcon(
            if (vm.fav != null) R.drawable.favourite_on_themed else R.drawable.favourite_off_themed
        )
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.extras?.getString(EXTRA_USER_ID)?.also { userId ->
            load(userId = userId)
            return true
        }
        intent.data?.also { data ->
            val url = data.toString()
            val userName = UiTools.userNameFromUrl(url)
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
        if (c.acc == null) return
        vm.user?.also { u ->
            if (!reset && (userId == u.id || userName == u.username)) return@load
        }
        if (::b.isInitialized && expandable.zoomed) expandable.collapse()

        CoroutineScope(Dispatchers.IO).launch {
            var userId_: String? = if (!reset) userId else vm.user!!.id()
            var userName_: String? = userName
            var userReplaced = false

            var pickle: Pickle? = null
            if (userId_ != null) {  // if the parameter `userId` is used
                vm.user?.also { oldUser ->
                    userReplaced = oldUser.id!! != userId_
                }
                pickle = Pickle(c.cacheDir, c.acc!!.id, Pickle.Type.PROFILE, userId_)
                val cache = if (!userReplaced && !reset) pickle.restore<Array<User>>() else null
                if (cache != null && cache.size == 2) {
                    vm.user = cache[0]
                    vm.profile = cache[1]
                } else {
                    vm.user = try {
                        SimpleJobs.userInfo(userId_)
                    } catch (e: Api.FailureException) {
                        onError(e.code)
                        return@launch
                    }
                    userName_ = vm.user!!.username!!

                    vm.profile = try {
                        SimpleJobs.profileInfo(userName_)
                    } catch (e: Api.FailureException) {
                        onError(e.code)
                        return@launch
                    }
                }

            } else {  // if the parameter `userName` is used
                vm.user?.also { oldUser ->
                    userReplaced = oldUser.username!! != userName_
                }
                vm.profile = try {
                    SimpleJobs.profileInfo(userName_!!)
                } catch (e: Api.FailureException) {
                    onError(e.code)
                    return@launch
                }
                userId_ = vm.profile!!.id!!
                if (vm.user == null) vm.user = try {
                    SimpleJobs.userInfo(userId_)
                } catch (e: Api.FailureException) {
                    onError(e.code)
                    return@launch
                }
            }

            if (pickle == null) pickle =
                Pickle(c.cacheDir, c.acc!!.id, Pickle.Type.PROFILE, userId_)
            pickle.save(arrayOf(vm.user!!, vm.profile!!))

            if (reset || userReplaced) {
                vm.posts = null
                vm.story = null
                vm.highlights = null
                vm.tagged = null
            }
            if (!userReplaced)
                vm.fav = c.fav.value?.find { it.id == vm.user!!.id!! }
            else
                vm.reloadUser = false

            withContext(Dispatchers.Main) {
                if (userReplaced) (currentPage() as BasePageViewer?)?.clear()
                (currentPage() as? PageVwr)?.showProfile(reset)
                b.toolbar.title = vm.user?.username
                fixTbMenu()
            }
        }
    }

    private suspend fun onError(code: Int) {
        withContext(Dispatchers.Main) {
            if (vm.user != null) {
                (currentPage() as? PageVwr)?.b?.refresher?.isRefreshing =
                    false  // in case of a refresh
                UiTools.snackbar(b.root, UiTools.apiError(c, code))
            } else {
                Toast.makeText(c, UiTools.apiError(c, code), Toast.LENGTH_LONG).show()
                @Suppress("DEPRECATION") super.onBackPressed()
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtInsta -> vm.user?.username?.also { UiTools.openProfile(this, it) }
            R.id.vtFav -> vm.user?.also { u ->
                if (vm.fav == null) {
                    vm.fav = Favourite(
                        u.id(),
                        u.username!!,
                        u.full_name!!,
                        u.profile_pic_url!!
                    )
                    c.addFavourite(vm.fav!!)
                } else {
                    c.removeFavourite(vm.fav!!)
                    vm.fav = null
                }
                fixTbMenu()
            }
            R.id.vtShortcut -> vm.user?.also { u ->
                val header = ((currentPage() as? PageVwr)?.b?.rv?.adapter as ConcatAdapter?)
                    ?.adapters[0] as PageVwr.Header? ?: return@also
                val bmp = (header.proPic.drawable as BitmapDrawable?)?.bitmap ?: return@also
                ShortcutManagerCompat.requestPinShortcut(
                    c, ShortcutInfoCompat.Builder(c, u.username!!).apply {
                        setIntent(
                            Intent(Intent.ACTION_VIEW, Utils.PROFILE.format(u.username).toUri())
                                .setPackage(UiTools.INSTA_PACKAGE)
                        )
                        setIcon(IconCompat.createWithBitmap(bmp.scale(128, 128)))
                        setShortLabel(u.full_name!!.ifBlank { u.username!! })
                    }.build(), null
                )
                c.incrementCounter(Settings.spShortcutCount)
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
        if (vm.currentPage != 1) {
            turnToPage(1); return; }
        vm.reset()
        @Suppress("DEPRECATION") super.onBackPressed()
    }
}
