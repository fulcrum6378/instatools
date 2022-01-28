package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Model
import kotlin.reflect.KClass

abstract class BaseActivity(private val isMain: Boolean = false) : AppCompatActivity() {
    lateinit var c: Context
    lateinit var m: Model
    lateinit var esp: SharedPreferences
    lateinit var gsp: SharedPreferences
    var sp: SharedPreferences? = null
    lateinit var dm: DisplayMetrics
    var night = false
    var dirRtl = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        c = applicationContext
        m = ViewModelProvider(this, Model.Factory()).get("Model", Model::class.java)
        esp = initEsp(c)
        gsp = getSharedPreferences("-1", Context.MODE_PRIVATE)
        initSp()
        dm = resources.displayMetrics
        night = c.resources.getBoolean(R.bool.night)
        dirRtl = c.resources.getBoolean(R.bool.dirRtl)
    }

    override fun setContentView(root: View?) {
        super.setContentView(root)
        root?.layoutDirection =
            if (!dirRtl) ViewGroup.LAYOUT_DIRECTION_LTR else ViewGroup.LAYOUT_DIRECTION_RTL
    }

    var tbTitle: TextView? = null
    fun toolbar(tb: Toolbar, title: Int) {
        if (!isMain) setSupportActionBar(tb)
        for (g in 0 until tb.childCount) {
            val getTitle = tb.getChildAt(g)
            if (getTitle is TextView &&
                getTitle.text.toString() == resources.getString(title)
            ) tbTitle = getTitle
        }
        //tbTitle?.typeface = font1Bold
        tbTitle?.textSize = resources.getDimension(R.dimen.tbTitle)
        if (!isMain) supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        //tb.navigationIcon?.apply { colorFilter = pdcf(R.color.CP) }
    }

    fun themeInflater(which: Theme, inf: LayoutInflater = layoutInflater): LayoutInflater =
        inf.cloneInContext(ContextThemeWrapper(c, which.res))

    fun color(res: Int) = ContextCompat.getColor(c, res)

    fun pdcf(res: Int) =
        PorterDuffColorFilter(ContextCompat.getColor(c, res), PorterDuff.Mode.SRC_IN)

    fun goTo(activity: KClass<*>, finish: Boolean = false): Boolean {
        startActivity(Intent(this, activity.java))
        if (finish) finish()
        return true
    }

    fun initSp() {
        if (m.acc != null)
            sp = getSharedPreferences(m.acc!!.id.toString(), Context.MODE_PRIVATE)
    }

    fun preference(key: String): String? =
        sp?.getString(key, null) ?: gsp.getString(key, null)

    enum class Theme(val res: Int) {
        DEFAULT(R.style.Theme_InstaTools),
        PRIMARY(R.style.Theme_InstaTools_Primary),
        SECONDARY(R.style.Theme_InstaTools_Secondary),
        TERTIARY(R.style.Theme_InstaTools_Tertiary)
    }

    companion object {
        fun initEsp(c: Context) = EncryptedSharedPreferences.create(
            "main", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), c,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
