package ir.mahdiparastesh.instatools.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.forEach
import androidx.core.view.get
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.internal.BaselineLayout
import java.util.*

class UiTools {
    companion object {
        const val PROFILE = "https://www.instagram.com/%s/"

        fun bnvTitles(bnv: BottomNavigationView): List<AppCompatTextView> {
            val list = ArrayList<AppCompatTextView>()
            (bnv[0] as BottomNavigationMenuView).forEach {
                val item = it as BottomNavigationItemView
                // 0 => FrameLayout (icon) and 1 => BaselineLayout (title)
                val title = item[1] as BaselineLayout // has 2 AppCompatTextView
                list.add(title[0] as AppCompatTextView) // normal state
                list.add(title[1] as AppCompatTextView) // selected state
            }
            return list.toList()
        }

        fun View.vis(bb: Boolean = true) {
            visibility = if (bb) View.VISIBLE else View.GONE
        }

        fun View.vish(bb: Boolean = true) {
            visibility = if (bb) View.VISIBLE else View.INVISIBLE
        }

        fun openProfile(c: AppCompatActivity, user: String) {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(PROFILE.format(user)))
            )
        }

        fun z(n: Int): String {
            val s = n.toString()
            return if (s.length == 1) "0$s" else s
        }

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        fun shake(c: Context, dur: Long = 55L) {
            val vib = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
            else vib.vibrate(dur)
        }

        fun date(time: Any): String {
            val cal = when (time) {
                is Long -> calendar(time)
                is Double -> calendar(time)
                else -> throw IllegalArgumentException("Unsupported unix time type!")
            }
            return "${cal[Calendar.YEAR]}.${z(cal[Calendar.MONTH] + 1)}." +
                    "${z(cal[Calendar.DAY_OF_MONTH])} - ${z(cal[Calendar.HOUR_OF_DAY])}:" +
                    "${z(cal[Calendar.MINUTE])}:${z(cal[Calendar.SECOND])}"
        }

        @Suppress("DEPRECATION")
        fun TextView.anchor(text: String?, url: String?) {
            if (text == null || url == null) {
                movementMethod = null
                setText("")
                return
            }
            movementMethod = LinkMovementMethod.getInstance()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                setText(Html.fromHtml("<a href=\"$url\">$text</a>", Html.FROM_HTML_MODE_LEGACY))
            else setText(Html.fromHtml("<a href=\"$url\">$text</a>"))
        }

        fun calendar(unix: Long): Calendar =
            Calendar.getInstance().apply { timeInMillis = unix }

        fun calendar(unix: Double): Calendar =
            Calendar.getInstance().apply { timeInMillis = instaTime(unix) }

        fun instaTime(time: Double) = time.toLong() / 1000L
    }
}
