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
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.forEach
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.view.MaterialMenu.Companion.stylise
import kotlin.reflect.KClass

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseActivity : AppCompatActivity(), Persistent, OnInitializationCompleteListener {
    val dbLazy = lazy { Database.build(c, (m.acc?.id ?: -1L).toString()) }
    val db: Database by dbLazy
    val dao: Database.DAO by lazy { db.dao() }
    val dm: DisplayMetrics by lazy { resources.displayMetrics }
    val fontBold: Typeface by lazy { font(getString(R.string.font_bold)) }
    val fontRegular: Typeface by lazy { font(getString(R.string.font_regular)) }
    val fontLight: Typeface by lazy { font(getString(R.string.font_light)) }
    val dirRtl by lazy { c.resources.getBoolean(R.bool.dirRtl) }
    val colorAc = MutableLiveData<Int?>(null)

    abstract val menuRes: Int?
    abstract val com: Alive
    override lateinit var c: Context
    override lateinit var m: Model
    override lateinit var gsp: SharedPreferences
    override var sp: SharedPreferences? = null

    companion object {
        var isAdsSdkInitialized = false
        var adsInitStatus: InitializationStatus? = null

        fun anyActive() = arrayOf(
            Main, Login, Downloads, Viewer, Favourites, MassFollower, Settings
        ).any { it.active }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.active = true
        resolvedIntent = null
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
        tbTitle?.typeface = font
        tbTitle?.textSize =
            resources.getDimension(if (this is Main) R.dimen.tbTitleMain else R.dimen.tbTitle)
        if (this !is Main) supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (menuRes != null) toolbar.inflateMenu(menuRes!!)
        if (!night()) colorAc.value ?: TypedValue().apply {
            theme.resolveAttribute(R.attr.colorPrimaryDark, this, true)
        }.data.apply {
            val cf = PorterDuffColorFilter(this, PorterDuff.Mode.SRC_IN)
            toolbar.menu.forEach { item ->
                item.icon?.colorFilter = cf
                item.stylise(this@BaseActivity)
            }
            if (this@BaseActivity !is Main) {
                toolbar.navigationIcon?.colorFilter = cf
                tbTitle?.setTextColor(this)
            }
            toolbar.overflowIcon?.colorFilter = cf
        }
        return true
    }

    override fun onDestroy() {
        com.handler = null
        com.active = false
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

    fun night(): Boolean = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    @Suppress("unused")
    enum class Theme(val res: Int) {
        DEFAULT(R.style.Theme_InstaTools),
        PRIMARY(R.style.Theme_InstaTools_Primary),
        SECONDARY(R.style.Theme_InstaTools_Secondary),
        TERTIARY(R.style.Theme_InstaTools_Tertiary),
        TERTIARY_LIGHT(R.style.Theme_InstaTools_Tertiary_Light)
    }
}
