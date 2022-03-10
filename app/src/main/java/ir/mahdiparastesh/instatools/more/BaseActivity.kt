package ir.mahdiparastesh.instatools.more

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.forEach
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.ads.*
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.view.MaterialMenu.Companion.stylise
import ir.mahdiparastesh.instatools.view.UiTools.Companion.bolden
import kotlin.reflect.KClass

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseActivity : AppCompatActivity(), Persistent, OnInitializationCompleteListener,
    Toolbar.OnMenuItemClickListener {
    val dbLazy = lazy { Database.build(c, (m.acc?.id ?: -1L).toString()) }
    val db: Database by dbLazy
    val dao: Database.DAO by lazy { db.dao() }
    val dm: DisplayMetrics by lazy { resources.displayMetrics }
    val fontBold: Typeface by lazy { font(getString(R.string.font_bold)) }
    val fontRegular: Typeface by lazy { font(getString(R.string.font_regular)) }
    val fontLight: Typeface by lazy { font(getString(R.string.font_light)) }
    val dirRtl by lazy { c.resources.getBoolean(R.bool.dirRtl) }
    val shallBolden by lazy { c.resources.getBoolean(R.bool.shallBolden) }
    val colorAc = MutableLiveData<Int?>(null)
    var interstitialAd: InterstitialAd? = null
    var loadingAd = false
    var showingAd = false
    var retryForAd = 0

    abstract val menuRes: Int?
    abstract val com: ActivityCompanion
    override lateinit var c: Context
    override lateinit var m: Model
    override lateinit var gsp: SharedPreferences
    override var sp: SharedPreferences? = null

    abstract class ActivityCompanion : Alive()

    companion object {
        var isAdsSdkInitialized = false
        var adsInitStatus: InitializationStatus? = null

        fun anyActive() = arrayOf(
            Main, Login, Downloads, Viewer, Favourites, MassFollower, Settings
        ).any { it.active.value!! }

        fun Context.night(): Boolean = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.active.value = true
        resolvedIntent = null
        notFirstResume = false
        super.onCreate(savedInstanceState)
        c = applicationContext
        m = ViewModelProvider(this, Model.Factory()).get("Model", Model::class.java)
        gsp = initGsp()
        if (this !is Login && m.acc == null)
            m.acc = Account.selected(this, guestIfNotExists = this !is Main)
        sp = initSp(m.acc)
        resolvedIntent = resolveIntent(intent, true)
        if (resolvedIntent == false) {
            super.onBackPressed()
            finish()
            return; }

        if (!isAdsSdkInitialized)
            Delay(3000) { MobileAds.initialize(c, this) }
        else if (adsInitStatus != null) onInitializationComplete(adsInitStatus!!)
    }

    override fun onNewIntent(intent: Intent) {
        resolvedIntent = null
        super.onNewIntent(intent)
        resolvedIntent = resolveIntent(intent, false)
    }

    var notFirstResume = false
    override fun onPause() {
        super.onPause()
        notFirstResume = true
    }

    var resolvedIntent: Boolean? = null
    open fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        return true // shall pass
    }

    override fun setContentView(root: View?) {
        super.setContentView(root)
        root?.layoutDirection =
            if (!dirRtl) ViewGroup.LAYOUT_DIRECTION_LTR else ViewGroup.LAYOUT_DIRECTION_RTL
    }

    override fun onInitializationComplete(adsInitStatus: InitializationStatus) {
        isAdsSdkInitialized = true
    }

    var tbTitle: TextView? = null
    lateinit var toolbar: Toolbar
    fun toolbar(tb: Toolbar, title: Int, font: Typeface = fontBold, changeTitleTo: String? = null) {
        toolbar = tb
        setSupportActionBar(tb)
        for (g in 0 until tb.childCount) {
            val getTitle = tb.getChildAt(g)
            if (getTitle is TextView &&
                getTitle.text.toString() == resources.getString(title)
            ) tbTitle = getTitle
        }
        if (changeTitleTo != null) tbTitle?.text = changeTitleTo
        tbTitle?.bolden(this, font)
        if (this !is Main) supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (menuRes != null) toolbar.inflateMenu(menuRes!!)
        if (!night()) colorAc.value ?: TypedValue().apply {
            theme.resolveAttribute(R.attr.colorPrimary, this, true)
            val cf = PorterDuffColorFilter(data, PorterDuff.Mode.SRC_IN)
            toolbar.menu.forEach { item ->
                item.icon?.colorFilter = cf
                item.stylise(this@BaseActivity)
            }
            if (this@BaseActivity !is Main) {
                toolbar.navigationIcon?.colorFilter = cf
                tbTitle?.setTextColor(data)
            }
            toolbar.overflowIcon?.colorFilter = cf
        }
        toolbar.setOnMenuItemClickListener(this)
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = true

    @MainThread
    fun loadInterstitial(adUnitId: String, autoPlay: () -> Boolean) {
        if (!isAdsSdkInitialized) {
            if (retryForAd < 2) Delay(2000L) {
                loadInterstitial(adUnitId)
                retryForAd++
            } else retryForAd = 0
            return; }
        if (interstitialAd != null || loadingAd) return
        loadingAd = true
        InterstitialAd.load(
            c, adUnitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    loadingAd = false
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingAd = false
                    interstitialAd = ad.apply { fullScreenContentCallback = InterstitialCallback() }
                    if (autoPlay()) showInterstitial()
                }
            })
    }

    fun loadInterstitial(adUnitId: String, autoPlay: Boolean = false) {
        loadInterstitial(adUnitId) { autoPlay }
    }

    @MainThread
    fun showInterstitial() {
        if (showingAd) return
        interstitialAd?.show(this@BaseActivity)
    }

    override fun onDestroy() {
        com.handler = null
        com.active.value = false
        if (dbLazy.isInitialized() && !Alive.anyLiving()) db.close()
        super.onDestroy()
    }

    fun themeInflater(which: Theme, inf: LayoutInflater = layoutInflater): LayoutInflater =
        inf.cloneInContext(ContextThemeWrapper(c, which.res))

    fun color(@ColorRes res: Int) = ContextCompat.getColor(c, res)

    fun drawable(@DrawableRes res: Int, @ColorRes cf: Int? = null) =
        ContextCompat.getDrawable(c, res)?.apply { cf?.let { colorFilter = pdcf(it) } }

    fun pdcf(@ColorRes res: Int) =
        PorterDuffColorFilter(ContextCompat.getColor(c, res), PorterDuff.Mode.SRC_IN)

    fun font(path: String): Typeface = Typeface.createFromAsset(c.assets, path)

    // Only for TextView.textSize
    fun dimen(@DimenRes res: Int): Float = resources.getDimension(res) / dm.density

    fun goTo(
        activity: KClass<*>,
        finish: Boolean = false,
        onIntent: (Intent.() -> Unit)? = null
    ): Boolean {
        startActivity(
            Intent(this, activity.java).apply { onIntent?.let { it() } },
            ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        )
        if (finish) Delay(1000) { finish() }
        // The phone's home screen may appear if there are no active activities at the moment.
        return true
    }

    fun weaken(@ColorInt it: Int, alpha: Int = 100) = Color.argb(alpha, it.red, it.green, it.blue)

    fun launcher(callback: ActivityResultCallback<ActivityResult>) =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult(), callback)

    @Suppress("unused")
    enum class Theme(val res: Int) {
        DEFAULT(R.style.Theme_InstaTools),
        PRIMARY(R.style.Theme_InstaTools_Primary),
        SECONDARY(R.style.Theme_InstaTools_Secondary),
        TERTIARY(R.style.Theme_InstaTools_Tertiary),
        TERTIARY_LIGHT(R.style.Theme_InstaTools_Tertiary_Light)
    }

    inner class InterstitialCallback : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            showingAd = true
        }

        override fun onAdFailedToShowFullScreenContent(adError: AdError?) {
            showingAd = false
            interstitialAd = null
        }

        override fun onAdDismissedFullScreenContent() {
            showingAd = false
            interstitialAd = null
        }
    }
}
