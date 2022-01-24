package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Model

open class BaseActivity : AppCompatActivity() {
    lateinit var c: Context
    lateinit var m: Model
    lateinit var sp: SharedPreferences
    lateinit var dm: DisplayMetrics
    var night = false
    var dirRtl = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        c = applicationContext
        m = ViewModelProvider(this, Model.Factory()).get("Model", Model::class.java)

        sp = EncryptedSharedPreferences.create(
            "main", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), c,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        dm = resources.displayMetrics
        night = c.resources.getBoolean(R.bool.night)
        dirRtl = c.resources.getBoolean(R.bool.dirRtl)
    }

    override fun setContentView(root: View?) {
        super.setContentView(root)
        root?.layoutDirection =
            if (!dirRtl) ViewGroup.LAYOUT_DIRECTION_LTR else ViewGroup.LAYOUT_DIRECTION_RTL
    }

    fun themeInflator(which: Theme, inf: LayoutInflater = layoutInflater) =
        inf.cloneInContext(ContextThemeWrapper(c, which.res))

    fun color(res: Int) = ContextCompat.getColor(c, res)

    fun pdcf(res: Int) =
        PorterDuffColorFilter(ContextCompat.getColor(c, res), PorterDuff.Mode.SRC_IN)

    enum class Theme(val res: Int) {
        DEFAULT(R.style.Theme_InstaTools),
        PRIMARY(R.style.Theme_InstaTools_Primary),
        SECONDARY(R.style.Theme_InstaTools_Secondary),
        TERTIARY(R.style.Theme_InstaTools_Tertiary)
    }
}
