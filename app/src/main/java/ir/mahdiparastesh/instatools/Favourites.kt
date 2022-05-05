package ir.mahdiparastesh.instatools

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.initialization.InitializationStatus
import ir.mahdiparastesh.instatools.databinding.FavouritesBinding
import ir.mahdiparastesh.instatools.list.ListFav
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.isReady
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish
import kotlinx.coroutines.runBlocking

class Favourites : BaseActivity() {
    private lateinit var b: FavouritesBinding
    private lateinit var adBanner: AdView

    override val menuRes: Int? = null
    override val com: ActivityCompanion get() = Companion

    companion object : ActivityCompanion() {
        const val HANDLE_LOADED = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = FavouritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        initToolbar(b.toolbar, R.string.favourites)

        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LOADED -> {
                        b.refresher.isRefreshing = false
                        adapt()
                    }
                }
            }
        }

        b.refresher.setOnRefreshListener { load() }
        b.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                b.tbShadow.vish(b.rv.computeVerticalScrollOffset() > 0)
                updateJumper()
            }
        })
        b.jumper.setOnClickListener { b.rv.smoothScrollToPosition(0) }
        b.jumper.translationY = UiTools.jumperTrans(this)
        shouldShowJumper.observe(this) {
            anJumper?.cancel()
            anJumper = UiTools.anJumper(this, b.jumper, it)
        }
        b.empty.typeface = fontRegular

        load()
    }

    override fun onInitializationComplete(adsInitStatus: InitializationStatus) {
        super.onInitializationComplete(adsInitStatus)
        if (!adsInitStatus.isReady()) return
        adBanner = UiTools.adaptiveBanner(this, R.string.bnrBtmFavourites)
        b.root.addView(adBanner, UiTools.adaptiveBannerLp())
        adBanner.loadAd(AdRequest.Builder().build())
        b.refresher.layoutParams = (b.refresher.layoutParams as ConstraintLayout.LayoutParams)
            .apply { bottomToTop = R.id.adBanner }
    }

    override fun onResume() {
        super.onResume()
        if (notFirstResume) load()
    }

    private fun load() {
        FavLoader(this).start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun adapt() {
        if (m.fav!!.isNotEmpty()) {
            if (b.rv.adapter == null) b.rv.adapter = ListFav(this@Favourites)
            else b.rv.adapter?.notifyDataSetChanged()
        } else {
            b.rv.vis(false)
            b.empty.vis(true)
        }
    }

    private var shouldShowJumper = MutableLiveData(false)
    private var anJumper: ObjectAnimator? = null
    private fun updateJumper() {
        (b.rv.computeVerticalScrollOffset() > dm.heightPixels)
            .apply { if (this != shouldShowJumper.value) shouldShowJumper.value = this }
    }

    class FavLoader(private val c: BaseActivity) : Thread() {
        override fun run() {
            c.dao.deleteExportables()
            runBlocking { c.m.fav = ArrayList(c.dao.favourites()) }
            handler?.obtainMessage(HANDLE_LOADED)?.sendToTarget()
        }
    }
}
