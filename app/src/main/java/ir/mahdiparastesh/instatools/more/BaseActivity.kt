package ir.mahdiparastesh.instatools.more

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.forEach
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.view.MaterialMenu.Companion.stylise
import kotlin.reflect.KClass

abstract class BaseActivity(private val isMain: Boolean = false) : AppCompatActivity(), Persistent {
    protected lateinit var db: Database
    lateinit var dao: Database.DAO
    lateinit var dm: DisplayMetrics
    lateinit var fontBold: Typeface
    lateinit var fontRegular: Typeface
    lateinit var fontLight: Typeface
    var night = false
    var dirRtl = false
    abstract val menuRes: Int?
    val colorAc = MutableLiveData<Int?>(null)

    override lateinit var c: Context
    override lateinit var m: Model
    override lateinit var gsp: SharedPreferences
    override var sp: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        c = applicationContext
        m = ViewModelProvider(this, Model.Factory()).get("Model", Model::class.java)
        gsp = Persistent.initGsp(c)
        if (m.acc == null) m.acc = Account.selected(this, guestIfNotExists = false) // needs "gsp"
        sp = Persistent.initSp(c, m.acc)

        dm = resources.displayMetrics
        night = c.resources.getBoolean(R.bool.night)
        dirRtl = c.resources.getBoolean(R.bool.dirRtl)
        fontBold = font(getString(R.string.font_bold))
        fontRegular = font(getString(R.string.font_regular))
        fontLight = font(getString(R.string.font_light))
    }

    override fun setContentView(root: View?) {
        super.setContentView(root)
        root?.layoutDirection =
            if (!dirRtl) ViewGroup.LAYOUT_DIRECTION_LTR else ViewGroup.LAYOUT_DIRECTION_RTL
        if (!isMain && night) TypedValue().apply {
            theme.resolveAttribute(R.attr.colorPrimaryDark, this, true)
        }.data.apply {
            window.decorView.setBackgroundColor(this)
            window.statusBarColor = this
            window.navigationBarColor = this
        }
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
            resources.getDimension(if (isMain) R.dimen.tbTitleMain else R.dimen.tbTitle)
        if (!isMain) supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (menuRes != null) toolbar.inflateMenu(menuRes!!)
        if (!night) colorAc.value ?: TypedValue().apply {
            theme.resolveAttribute(R.attr.colorPrimaryDark, this, true)
        }.data.apply {
            val cf = PorterDuffColorFilter(this, PorterDuff.Mode.SRC_IN)
            toolbar.menu.forEach { item ->
                item.icon?.colorFilter = cf
                item.stylise(this@BaseActivity)
            }
            if (!isMain) {
                toolbar.navigationIcon?.colorFilter = cf
                tbTitle?.setTextColor(this)
            }
            toolbar.overflowIcon?.colorFilter = cf
        }
        return true
    }

    override fun onDestroy() {
        // if (::db.isInitialized && !Inquisitor.active && !Exporter.active && !Queuer.active) db.close()
        super.onDestroy()
    }

    fun themeInflater(which: Theme, inf: LayoutInflater = layoutInflater): LayoutInflater =
        inf.cloneInContext(ContextThemeWrapper(c, which.res))

    fun color(res: Int) = ContextCompat.getColor(c, res)

    fun pdcf(res: Int) =
        PorterDuffColorFilter(ContextCompat.getColor(c, res), PorterDuff.Mode.SRC_IN)

    fun font(path: String): Typeface = Typeface.createFromAsset(c.assets, path)

    fun goTo(activity: KClass<*>, finish: Boolean = false): Boolean {
        startActivity(
            Intent(this, activity.java),
            ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        )
        if (finish) Delay(500) { finish() }
        // The phone's home screen may appear if there are no active activities at the moment.
        return true
    }

    fun weaken(@ColorInt it: Int, alpha: Int = 100) = Color.argb(alpha, it.red, it.green, it.blue)

    fun isDbInitialised() = ::db.isInitialized

    @Suppress("unused")
    enum class Theme(val res: Int) {
        DEFAULT(R.style.Theme_InstaTools),
        PRIMARY(R.style.Theme_InstaTools_Primary),
        SECONDARY(R.style.Theme_InstaTools_Secondary),
        TERTIARY(R.style.Theme_InstaTools_Tertiary),
        TERTIARY_LIGHT(R.style.Theme_InstaTools_Tertiary_Light)
    }
}
