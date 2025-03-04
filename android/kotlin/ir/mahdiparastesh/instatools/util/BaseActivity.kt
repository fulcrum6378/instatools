package ir.mahdiparastesh.instatools.util

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.forEach
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import kotlin.reflect.KClass

/** Abstract class for all Activity instances in this app and it extends [AppCompatActivity]. */
abstract class BaseActivity : AppCompatActivity(), Toolbar.OnMenuItemClickListener {
    val c: InstaTools by lazy { applicationContext as InstaTools }
    val dirRtl by lazy { c.resources.getBoolean(R.bool.dirRtl) }
    val colorAc = MutableLiveData<Int?>(null)

    abstract val menuRes: Int?

    companion object {
        fun Context.night(): Boolean = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (this !is Login && c.acc == null)
            goTo(Login::class)

        resolvedIntent = null
        super.onCreate(savedInstanceState)
        resolvedIntent = resolveIntent(intent, true)
        if (resolvedIntent == false) {
            @Suppress("DEPRECATION") super.onBackPressed(); finish(); return; }

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
        return true // shall pass
    }

    override fun setContentView(root: View?) {
        super.setContentView(root)
        root?.layoutDirection =
            if (!dirRtl) ViewGroup.LAYOUT_DIRECTION_LTR
            else ViewGroup.LAYOUT_DIRECTION_RTL
    }

    var tbTitle: TextView? = null
    lateinit var toolbar: Toolbar
    fun initToolbar(tb: Toolbar, title: Int, changeTitleTo: String? = null) {
        toolbar = tb
        setSupportActionBar(tb)
        for (g in 0 until tb.childCount) {
            val getTitle = tb.getChildAt(g)
            if (getTitle is TextView && getTitle.text.toString() == getString(title))
                tbTitle = getTitle
        }
        if (changeTitleTo != null) tbTitle?.text = changeTitleTo
        if (this !is Main) {
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                setDisplayShowHomeEnabled(true)
            }
            toolbar.setNavigationOnClickListener { @Suppress("DEPRECATION") onBackPressed() }
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

    fun wrapTheme(which: Theme): ContextThemeWrapper = ContextThemeWrapper(c, which.res)

    fun themeInflater(which: Theme, inf: LayoutInflater = layoutInflater): LayoutInflater =
        inf.cloneInContext(wrapTheme(which))

    /** Helper function for getting a colour from resources. */
    fun color(@ColorRes res: Int) = ContextCompat.getColor(this, res)

    /** Helper function for getting a drawable from resources with an optional colour filter. */
    fun drawable(@DrawableRes res: Int, @ColorRes cf: Int? = null) =
        ContextCompat.getDrawable(this, res)?.apply { cf?.let { colorFilter = pdcf(it) } }

    /** Helper function for making a colour filter for the color resource. */
    fun pdcf(@ColorRes res: Int) =
        PorterDuffColorFilter(ContextCompat.getColor(this, res), PorterDuff.Mode.SRC_IN)

    /** Only use it for TextView.textSize. */
    fun dimen(@DimenRes res: Int): Float = resources.getDimension(res) / c.dm.density

    /** Helper function for starting an Activity. */
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

    /** @return a weakened version of the colour using an alpha value. */
    fun weaken(@ColorInt it: Int, alpha: Int = 100) = Color.argb(alpha, it.red, it.green, it.blue)

    /** Helper function for registering a "startActivityForResult" action. */
    fun launcherForResult(callback: ActivityResultCallback<ActivityResult>) =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult(), callback)

    /** All themes used in this app. */
    @Suppress("unused")
    enum class Theme(val res: Int) {
        DEFAULT(R.style.Theme_InstaTools),
        PRIMARY(R.style.Theme_InstaTools_Primary),
        SECONDARY(R.style.Theme_InstaTools_Secondary),
        TERTIARY(R.style.Theme_InstaTools_Tertiary)
    }
}
