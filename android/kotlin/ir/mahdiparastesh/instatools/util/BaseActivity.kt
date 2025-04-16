package ir.mahdiparastesh.instatools.util

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toolbar
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.edit
import androidx.core.view.forEach
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.InstaTools
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import kotlin.reflect.KClass

/** Abstract class for all Activities in this app and it extends [FragmentActivity] */
abstract class BaseActivity : FragmentActivity(), Toolbar.OnMenuItemClickListener {
    val c: InstaTools by lazy { applicationContext as InstaTools }
    val dm: DisplayMetrics by lazy { resources.displayMetrics }
    val night: Boolean by lazy {
        resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
    val dirRtl by lazy { resources.getBoolean(R.bool.dirRtl) }
    val colorAc = MutableLiveData<Int?>(null)
    lateinit var toolbar: Toolbar
    var tbTitle: TextView? = null

    abstract val menuRes: Int?


    override fun onCreate(savedInstanceState: Bundle?) {
        if (this !is Login && c.acc == null)
            goTo(Login::class)

        resolvedIntent = null
        super.onCreate(savedInstanceState)
        resolvedIntent = resolveIntent(intent, true)
        if (resolvedIntent == false) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
            finish()
            return
        }

        if (intent.action in arrayOf(Intent.ACTION_MAIN, Intent.ACTION_SEND, Intent.ACTION_VIEW)) {
            c.incrementCounter(Settings.spOpenAppCount)
            if (!c.gsp.contains(Settings.spFirstOpenApp))
                c.gsp.edit { putLong(Settings.spFirstOpenApp, Utils.now()) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        resolvedIntent = null
        super.onNewIntent(intent)
        resolvedIntent = resolveIntent(intent, false)
    }

    var resolvedIntent: Boolean? = null
    open fun resolveIntent(intent: Intent, onCreation: Boolean): Boolean {
        return true  // shall pass
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        view?.layoutDirection =
            if (!dirRtl) ViewGroup.LAYOUT_DIRECTION_LTR
            else ViewGroup.LAYOUT_DIRECTION_RTL
    }

    @SuppressLint("UseSupportActionBar")
    fun initToolbar(tb: Toolbar, title: Int, changeTitleTo: String? = null) {
        toolbar = tb
        setActionBar(tb)
        for (g in 0 until tb.childCount) {
            val getTitle = tb.getChildAt(g)
            if (getTitle is TextView && getTitle.text.toString() == getString(title))
                tbTitle = getTitle
        }
        if (changeTitleTo != null) tbTitle?.text = changeTitleTo
        if (this !is Main) {
            actionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
            }
            toolbar.setNavigationOnClickListener {
                @Suppress("DEPRECATION")
                onBackPressed()
            }
        }
    }

    open fun styliseToolbar() {
        val ca = colorAc.value ?: themeColor()
        val cf = PorterDuffColorFilter(ca, PorterDuff.Mode.SRC_IN)
        toolbar.navigationIcon?.colorFilter = cf
        if (!night && this is Main) {
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

    fun wrapTheme(which: Theme): ContextThemeWrapper = ContextThemeWrapper(c, which.res)

    fun themeInflater(which: Theme, inf: LayoutInflater = layoutInflater): LayoutInflater =
        inf.cloneInContext(wrapTheme(which))

    /** Helper function for getting a colour from resources */
    fun color(@ColorRes res: Int) = resources.getColor(res, theme)

    /** Helper function for getting a drawable from resources with an optional colour filter */
    @SuppressLint("UseCompatLoadingForDrawables")
    fun drawable(@DrawableRes res: Int, @ColorRes cf: Int? = null) =
        resources.getDrawable(res, theme)?.apply { cf?.let { colorFilter = pdcf(it) } }

    /** Helper function for making a colour filter for the color resource */
    fun pdcf(@ColorRes res: Int) =
        PorterDuffColorFilter(resources.getColor(res, theme), PorterDuff.Mode.SRC_IN)

    /** Only use it for [TextView.textSize]. */
    fun dimen(@DimenRes res: Int): Float = resources.getDimension(res) / dm.density

    /** Helper function for starting an Activity */
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

    /** Helper function for registering a "startActivityForResult" action */
    fun launcherForResult(callback: ActivityResultCallback<ActivityResult>) =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult(), callback)

    /** All themes used in this app */
    @Suppress("unused")
    enum class Theme(val res: Int) {
        DEFAULT(R.style.Theme_InstaTools),
        PRIMARY(R.style.Theme_InstaTools_Primary),
        SECONDARY(R.style.Theme_InstaTools_Secondary),
        TERTIARY(R.style.Theme_InstaTools_Tertiary)
    }
}
