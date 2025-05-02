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
import ir.mahdiparastesh.instatools.base.BaseActivity
import ir.mahdiparastesh.instatools.base.BasePageViewer
import ir.mahdiparastesh.instatools.base.MultiPagedActivity
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.frag.PageSto
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.job.SimpleJobs
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Viewer : MultiPagedActivity(PageSto::class, PageVwr::class, PageRel::class, PageTag::class),
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
        var profile: User? = null
        var posts: Page<Media>? = null
        var story: Story? = null
        var highlights: Page<Story>? = null
        var reels: Page<Media.Wrapper>? = null
        var tagged: Page<Media>? = null
        var currentPage = 1
        var fav: Favourite? = null
        //var reloadUser = false

        fun reset() {
            profile = null
            posts = null
            story = null
            highlights = null
            reels = null
            tagged = null
            currentPage = 1
            fav = null
            //reloadUser = false
        }
    }

    companion object {
        private const val EXTRA_USER_ID = "userId"
        private const val EXTRA_USER_NAME = "userName"

        fun comeHere(c: BaseActivity, userId: String, userName: String) {
            c.goTo(Viewer::class) {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_USER_NAME, userName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resolvedIntent == false) return
        b = ViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.vwTitle)

        // if the user is already loaded
        vm.profile?.also {
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
        if (intent.hasExtra(EXTRA_USER_ID)) {
            val userId = intent.getStringExtra(EXTRA_USER_ID)!!
            val userName = intent.getStringExtra(EXTRA_USER_NAME)!!
            load(userId, userName)
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
    fun load(
        userId: String? = null,
        userName: String? = null,
        reset: Boolean = false
    ) {
        if (c.acc == null) return
        vm.profile?.also { u ->
            if (!reset && (userId == u.id || userName == u.username)) return@load
        }
        if (::b.isInitialized && expandable.zoomed) expandable.collapse()

        CoroutineScope(Dispatchers.IO).launch {
            var userId_: String? = if (!reset) userId else vm.profile!!.id()
            var userName_: String = if (!reset) userName!! else vm.profile!!.username!!
            var profileReplaced = false
            val userIdWasAvailableAtFirst = userId_ != null

            // check if this is the same user that was previously loaded
            vm.profile?.also { oldUser ->
                profileReplaced = oldUser.username!! != userName_
            }

            // load from a pickle only if a user ID is provided
            var pickle: Pickle? = null
            var cache: User? = null
            if (userId_ != null) {
                pickle = Pickle(c.cacheDir, c.acc!!.id, Pickle.Type.PROFILE, userId_)
                cache = if (!profileReplaced && !reset) pickle.restore<User>() else null
            }

            // fetch profile info if a Pickle cache is not available
            vm.profile = cache ?: try {
                SimpleJobs.profileInfo(userName_)
            } catch (e: Api.FailureException) {
                if (userIdWasAvailableAtFirst && e.code == 404) {
                    // detect a recently changed user name
                    try {
                        SimpleJobs.profileInfo(
                            SimpleJobs.userInfo(userId_).username!!
                        )
                    } catch (e: Api.FailureException) {
                        onError(e.code)
                        return@launch
                    }
                } else {
                    onError(e.code)
                    return@launch
                }
            }

            // save as a pickle
            if (pickle == null) pickle =
                Pickle(c.cacheDir, c.acc!!.id, Pickle.Type.PROFILE, vm.profile!!.id())
            pickle.save(vm.profile!!)

            // clear the data models if this is a different profile or a reset action is demanded
            if (reset || profileReplaced) {
                vm.posts = null
                vm.story = null
                vm.highlights = null
                vm.reels = null
                vm.tagged = null
            }
            if (!profileReplaced) {
                // find out if this user is in the favourites list
                // and update its details if it is in the list
                vm.fav = c.fav.value?.find { it.id == vm.profile!!.id!! }?.also {
                    it.user = vm.profile!!.username!!
                    it.name = vm.profile!!.full_name!!
                    it.photo = vm.profile!!.profile_pic_url!!
                }
            } /*else
                vm.reloadUser = false*/

            withContext(Dispatchers.Main) {
                if (profileReplaced) (currentPage() as BasePageViewer?)?.clear()
                (currentPage() as? PageVwr)?.showProfile(reset)
                b.toolbar.title = vm.profile?.username
                fixTbMenu()
            }
        }
    }

    private suspend fun onError(code: Int) {
        withContext(Dispatchers.Main) {
            Toast.makeText(c, UiTools.apiError(c, code), Toast.LENGTH_LONG).show()
            if (vm.profile != null) {
                (currentPage() as? PageVwr)?.b?.refresher?.isRefreshing =
                    false  // in case of a refresh
                //UiTools.snackbar(b.root, UiTools.apiError(c, code))
            } else {
                //Toast.makeText(c, UiTools.apiError(c, code), Toast.LENGTH_LONG).show()
                @Suppress("DEPRECATION") super.onBackPressed()
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtInsta -> vm.profile?.username?.also { UiTools.openProfile(this, it) }
            R.id.vtFav -> vm.profile?.also { u ->
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
            R.id.vtShortcut -> vm.profile?.also { u ->
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

    override fun selective(
        bb: Boolean,
        selectiveToolbarMenuRes: Int?,
        selectiveToolbarListener: Toolbar.OnMenuItemClickListener?
    ): Boolean {
        if (!super.selective(bb, selectiveToolbarMenuRes, selectiveToolbarListener)) return false
        fixTbMenu()
        return true
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
