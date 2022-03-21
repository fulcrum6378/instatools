package ir.mahdiparastesh.instatools

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.MutableLiveData
import androidx.media2.common.SessionPlayer
import com.android.volley.NetworkResponse
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.data.Favourite
import ir.mahdiparastesh.instatools.databinding.ViewerBinding
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.list.ListCar
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_ABORTED
import ir.mahdiparastesh.instatools.more.BasePage.Companion.HANDLE_FETCHED
import ir.mahdiparastesh.instatools.more.BasePageViewer
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.more.TriplePageActivity
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.accFromUrl
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

class Viewer : TriplePageActivity<PageRel, PageVwr, PageTag>(), Toolbar.OnMenuItemClickListener {
    lateinit var b: ViewerBinding
    var user: String? = null
    private var dbFav: Favourite? = null
    private var thread: Initial? = null
    val expandable: Expandable by lazy {
        Expandable(
            this, b.expanded, handler, color(if (!night()) R.color.defBG else R.color.CS)
        ) { (pages()[currentPage.value!!] as BasePageViewer).updateShadow() }
    }

    override val menuRes = R.menu.viewer_tlb
    override val com: ActivityCompanion get() = Companion
    override val currentPage: MutableLiveData<Int> get() = m.vwCurrentPage
    override val aKlass: KClass<PageRel> = PageRel::class
    override val bKlass: KClass<PageVwr> = PageVwr::class
    override val cKlass: KClass<PageTag> = PageTag::class
    override val mode: TripleMode = TripleMode.VIEW_PAGER
    override fun defPage(): Int = 1

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
        toolbar(b.toolbar, R.string.vwTitle, changeTitleTo = user)
        createPages(b.pager)

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
                        Snackbar.make(b.root, R.string.loadFailed, Snackbar.LENGTH_LONG).show()
                    }
                    Api.HANDLE_ERROR -> {
                        b.refresher.isRefreshing = false
                        Snackbar.make(
                            b.root, c.getString(
                                R.string.unknownError,
                                (msg.obj as NetworkResponse?)?.statusCode.toString()
                            ), Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    PageSvd.HANDLE_INIT_QUEUER -> Downloads.initService(this@Viewer)
                    Expandable.HANDLE_EXPANDABLE_ERROR ->
                        Snackbar.make(b.root, R.string.unknownMyError, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        b.toolbar.setNavigationOnClickListener { onBackPressed() }
        b.refresher.setOnRefreshListener {
            reset()
            if (thread?.active != true) thread = Initial().also { it.start() }
        }
        b.refresher.setOnChildScrollUpCallback { _, _ ->
            return@setOnChildScrollUpCallback (pages()[currentPage.value!!] as BasePageViewer).avoidRefresh()
        }

        load(true)
    }

    override fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        intent.extras?.getString(EXTRA_USER)?.let {
            if (!onCreation && user == it) return false
            if (user != it) m.vwUser = null
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
                page1?.b?.rv?.adapter = null
            if (page2?.bInitialised == true) {
                page2?.b?.proPicIv?.setImageDrawable(null)
                page2?.b?.privateAcc?.vis(false)
            }
            if (page3?.bInitialised == true)
                page3?.b?.rv?.adapter = null
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        fixTbMenu()
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.vtInsta -> UiTools.openProfile(this, user!!)
            R.id.vtFav -> if (m.vwUser != null) CoroutineScope(Dispatchers.IO).launch {
                if (dbFav == null) {
                    dbFav = m.vwUser!!.favourite()
                    dao.addFavourite(dbFav!!)
                } else {
                    dao.deleteFavourite(dbFav!!)
                    dbFav = null
                }
                withContext(Dispatchers.Main) { fixTbMenu() }
            }
        }
        return super.onMenuItemClick(item)
    }

    fun fixTbMenu() {
        b.toolbar.menu.findItem(R.id.vtFav)
            ?.setIcon(if (dbFav != null) R.drawable.favourite else R.drawable.non_favourite)
    }

    private fun load(firstLoad: Boolean = false) {
        if (m.vwUser != null) {
            handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
            return; }
        reset(firstLoad)
        CoroutineScope(Dispatchers.IO).launch {
            dbFav = dao.favouriteByUser(user!!).getOrNull(0)
            withContext(Dispatchers.Main) { fixTbMenu() }
        }
        if (thread?.active != true) thread = Initial().also { it.start() }
    }

    private fun reset(firstLoad: Boolean = false) {
        if (!firstLoad) pages().forEach { (it as BasePageViewer).reset() }
        if (expandable.zoomed) expandable.collapse()
    }

    override fun onPause() {
        super.onPause()
        (b.expanded.slider.adapter as ListCar?)?.players
            ?.forEach { if (it?.playerState == SessionPlayer.PLAYER_STATE_PLAYING) it.pause() }
    }

    override fun onBackPressed() {
        if (pageGoBack()) return
        if (::b.isInitialized) {
            if (expandable.zoomed) {
                expandable.collapse(); return; }
        }
        m.vwUser = null
        m.vwReels = null
        m.vwTagged = null
        m.vwCurrentPage.value = 1
        super.onBackPressed()
    }

    inner class Initial : BaseThread() {
        override fun run() {
            Api<Profile>(
                this@Viewer, Api.Type.PROFILE.url.format(user), Profile::class,
                handler, onError = { interrupt() }
            ) { profile ->
                m.vwUser = profile.graphql?.user
                if (m.vwUser == null) {
                    Toast.makeText(c, R.string.pageNotExist, Toast.LENGTH_SHORT).show()
                    interrupt(); return@Api
                }
                handler?.obtainMessage(HANDLE_FETCHED)?.sendToTarget()
                interrupt()
            }
        }
    }
}
