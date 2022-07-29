package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.forEach
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

abstract class BaseActivity : AppCompatActivity(), Persistent, Toolbar.OnMenuItemClickListener {
    // , OnInitializationCompleteListener
    val dm: DisplayMetrics by lazy { resources.displayMetrics }
    val dirRtl by lazy { c.resources.getBoolean(R.bool.dirRtl) }
    val colorAc = MutableLiveData<Int?>(null)

    /*var interstitialAd: InterstitialAd? = null
    var loadingAd = false
    var showingAd = false
    private var retryForAd = 0*/

    abstract val menuRes: Int?
    abstract val com: ActivityCompanion
    override val c: Context get() = applicationContext
    final override val dbLazy = lazy { Database.build(c, (m.acc?.id ?: -1L).toString()) }
    override val db: Database by dbLazy
    override val dao: Database.DAO by lazy { db.dao() }
    override lateinit var m: Model
    override lateinit var gsp: SharedPreferences
    override var sp: SharedPreferences? = null

    abstract class ActivityCompanion : Alive()

    companion object {
        // const val ADMOB_DELAY = 2000L
        // const val MAX_AD_RETRY = 2
        // var adsInitStatus: InitializationStatus? = null

        fun anyActive() = arrayOf(
            Main, Login, Downloads, Viewer, Favourites, MassFollower, Settings
        ).any { it.active.value!! }

        fun Context.night(): Boolean = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        // fun areAdsReady() = adsInitStatus?.isReady() == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.active.value = true
        resolvedIntent = null
        notFirstResume = false
        super.onCreate(savedInstanceState)
        m = ViewModelProvider(this, Model.Factory())["Model", Model::class.java]
        gsp = initGsp()
        if ((gsp.contains(Login.spAccount) || this !is Main) && this !is Login) {
            if (m.acc == null) CoroutineScope(Dispatchers.IO).launch {
                m.acc = Account.selected(
                    this@BaseActivity, guestIfNotExists = this@BaseActivity !is Main
                )
                withContext(Dispatchers.Main) { onAccountSet() }
            } else onAccountSet()
        } else onAccountSet()
        resolvedIntent = resolveIntent(intent, true)
        if (resolvedIntent == false) {
            super.onBackPressed()
            finish()
            return; }

        if (intent.action in arrayOf(Intent.ACTION_MAIN, Intent.ACTION_SEND)) {
            incrementCounter(Settings.spOpenAppCount)
            if (!gsp.contains(Settings.spFirstOpenApp))
                gsp.edit { putLong(Settings.spFirstOpenApp, Persistent.now()) }
        }
    }

    var isAccountSet = false
    open fun onAccountSet() {
        if (m.acc?.id != -1L) sp = initSp(m.acc)
        isAccountSet = true
    }

    open fun onBuildUiBasedOnAccount() {
        uiBuildBasedOnAccount = true
    }

    override fun onNewIntent(intent: Intent) {
        resolvedIntent = null
        super.onNewIntent(intent)
        resolvedIntent = resolveIntent(intent, false)
    }

    var resolvedIntent: Boolean? = null
    open fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        return true // shall pass
    }

    protected var uiBuildBasedOnAccount = false
    override fun setContentView(root: View?) {
        super.setContentView(root)
        root?.layoutDirection =
            if (!dirRtl) ViewGroup.LAYOUT_DIRECTION_LTR else ViewGroup.LAYOUT_DIRECTION_RTL
        onBuildUiBasedOnAccount()
    }

    /*override fun onStart() {
        super.onStart()
        if (adsInitStatus?.isReady() != true)
            Delay(ADMOB_DELAY) { initAdmob() }
        else onInitializationComplete(adsInitStatus!!)
    }*/

    /*private fun initAdmob() {
        retryForAd = 0
        MobileAds.initialize(c, this)
    }*/

    /*override fun onInitializationComplete(adsInitStatus: InitializationStatus) {
        Companion.adsInitStatus = adsInitStatus
        if (!adsInitStatus.isReady()) {
            if (retryForAd < MAX_AD_RETRY) Delay(ADMOB_DELAY) {
                initAdmob()
                retryForAd++
            } else retryForAd = 0
            return; }
    }*/

    var tbTitle: AppCompatTextView? = null
    lateinit var toolbar: Toolbar
    fun initToolbar(tb: Toolbar, title: Int, changeTitleTo: String? = null) {
        toolbar = tb
        setSupportActionBar(tb)
        for (g in 0 until tb.childCount) {
            val getTitle = tb.getChildAt(g)
            if (getTitle is AppCompatTextView && getTitle.text.toString() == getString(title))
                tbTitle = getTitle
        }
        if (changeTitleTo != null) tbTitle?.text = changeTitleTo
        if (this !is Main) {
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
            }
            toolbar.setNavigationOnClickListener { onBackPressed() }
        }
    }

    open fun styliseToolbar() {
        val ca = colorAc.value ?: themeColor()
        val cf = PorterDuffColorFilter(ca, PorterDuff.Mode.SRC_IN)
        toolbar.navigationIcon?.colorFilter = cf
        if (!night() && this is Main) {
            tbTitle?.setTextColor(ca)
            toolbar.menu.forEach { item -> item.icon?.colorFilter = cf }
        }
        toolbar.overflowIcon?.colorFilter = cf
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!::toolbar.isInitialized) {
            Delay(1000L) { onCreateOptionsMenu(menu) }
            return false; }
        super.onCreateOptionsMenu(menu)
        if (menuRes != null) toolbar.inflateMenu(menuRes!!)
        styliseToolbar()
        toolbar.setOnMenuItemClickListener(this)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (!::toolbar.isInitialized) {
            Delay(3000L) { onPrepareOptionsMenu(menu) }
            return false; }
        return true
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = true

    /*@MainThread
    fun loadInterstitial(@StringRes adUnitId: Int, autoPlay: () -> Boolean) {
        if (adsInitStatus?.isReady() != true) {
            if (retryForAd < MAX_AD_RETRY) Delay(ADMOB_DELAY) {
                loadInterstitial(adUnitId)
                retryForAd++
            } else retryForAd = 0
            return; }
        if (interstitialAd != null || loadingAd) return
        loadingAd = true
        InterstitialAd.load(
            c, getString(adUnitId), AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
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

    fun loadInterstitial(@StringRes adUnitId: Int, autoPlay: Boolean = false) {
        loadInterstitial(adUnitId) { autoPlay }
    }

    @MainThread
    fun showInterstitial() {
        if (!showingAd) interstitialAd?.show(this@BaseActivity)
    }*/

    var notFirstResume = false
    override fun onPause() {
        super.onPause()
        notFirstResume = true
    }

    override fun switchAcc() {
        db.close()
        super.switchAcc()
    }

    override fun onDestroy() {
        com.handler = null
        com.active.value = false
        if (dbLazy.isInitialized() && !Alive.anyLiving()) db.close()
        super.onDestroy()
    }

    fun wrapTheme(which: Theme): ContextThemeWrapper = ContextThemeWrapper(c, which.res)

    fun themeInflater(which: Theme, inf: LayoutInflater = layoutInflater): LayoutInflater =
        inf.cloneInContext(wrapTheme(which))

    fun color(@ColorRes res: Int) = ContextCompat.getColor(this, res)

    fun drawable(@DrawableRes res: Int, @ColorRes cf: Int? = null) =
        ContextCompat.getDrawable(this, res)?.apply { cf?.let { colorFilter = pdcf(it) } }

    fun pdcf(@ColorRes res: Int) =
        PorterDuffColorFilter(ContextCompat.getColor(this, res), PorterDuff.Mode.SRC_IN)

    // Only for TextView.textSize
    fun dimen(@DimenRes res: Int): Float = resources.getDimension(res) / dm.density

    fun goTo(
        activity: KClass<*>,
        finish: Boolean = false, // USE THIS CAREFULLY
        animate: Boolean = false,
        onIntent: (Intent.() -> Unit)? = null
    ): Boolean {
        val intent = Intent(this, activity.java)
        onIntent?.also { intent.it() }
        if (animate) startActivity(
            intent, ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle()
            // this animation is the cause of occasional ugly activity-on-activity accidents!
        ) else startActivity(intent)
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

    /*inner class InterstitialCallback : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            showingAd = true
        }

        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            showingAd = false
            interstitialAd = null
        }

        override fun onAdDismissedFullScreenContent() {
            showingAd = false
            interstitialAd = null
        }
    }*/
}
