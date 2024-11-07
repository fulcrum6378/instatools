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
import ir.mahdiparastesh.instatools.data.Account.Companion.dbName
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/** Abstract class for all Activity instances in this app and it extends AppCompatActivity. */
abstract class BaseActivity : AppCompatActivity(), Persistent, Toolbar.OnMenuItemClickListener {
    val dm: DisplayMetrics by lazy { resources.displayMetrics }
    val dirRtl by lazy { c.resources.getBoolean(R.bool.dirRtl) }
    val colorAc = MutableLiveData<Int?>(null)

    abstract val menuRes: Int?
    abstract val com: ActivityCompanion
    override val c: Context get() = applicationContext
    final override val dbLazy = lazy { Database.build(c, m.acc.dbName()) }
    override val db: Database by dbLazy
    override val dao: Database.DAO by lazy { db.dao() }
    override lateinit var m: Model
    override lateinit var gsp: SharedPreferences
    override var sp: SharedPreferences? = null

    /** Abstract class from which all companion objects of BaseActivity subclasses must extend. */
    abstract class ActivityCompanion : Alive()

    companion object {
        fun anyActive() = arrayOf(Main, Login, Downloads, Viewer, Favourites, Settings)
            .any { it.active.value!! }

        fun Context.night(): Boolean = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.active.value = true
        resolvedIntent = null
        notFirstResume = false
        super.onCreate(savedInstanceState)
        m = ViewModelProvider(this, Model.Factory())["Model", Model::class.java]
        gsp = initGsp()
        if ((gsp.contains(Login.SP_ACCOUNT) || this !is Main) && this !is Login) {
            if (m.acc == null) CoroutineScope(Dispatchers.IO).launch {
                m.acc = Account.selected(
                    this@BaseActivity, guestIfNotExists = this@BaseActivity !is Main
                )
                withContext(Dispatchers.Main) { onAccountSet() }
            } else onAccountSet()
        } else onAccountSet()
        resolvedIntent = resolveIntent(intent, true)
        if (resolvedIntent == false) {
            @Suppress("DEPRECATION") super.onBackPressed(); finish(); return; }

        if (intent.action in arrayOf(Intent.ACTION_MAIN, Intent.ACTION_SEND, Intent.ACTION_VIEW)) {
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

    /** Helper function for getting a colour from resources. */
    fun color(@ColorRes res: Int) = ContextCompat.getColor(this, res)

    /** Helper function for getting a drawable from resources with an optional colour filter. */
    fun drawable(@DrawableRes res: Int, @ColorRes cf: Int? = null) =
        ContextCompat.getDrawable(this, res)?.apply { cf?.let { colorFilter = pdcf(it) } }

    /** Helper function for making a colour filter for the color resource. */
    fun pdcf(@ColorRes res: Int) =
        PorterDuffColorFilter(ContextCompat.getColor(this, res), PorterDuff.Mode.SRC_IN)

    /** Only use it for TextView.textSize. */
    fun dimen(@DimenRes res: Int): Float = resources.getDimension(res) / dm.density

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
        TERTIARY(R.style.Theme_InstaTools_Tertiary),
        TERTIARY_LIGHT(R.style.Theme_InstaTools_Tertiary_Light)
    }
}
