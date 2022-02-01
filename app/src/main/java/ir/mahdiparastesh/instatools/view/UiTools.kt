package ir.mahdiparastesh.instatools.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
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

        fun vis(v: View, bb: Boolean = true) {
            v.visibility = if (bb) View.VISIBLE else View.GONE
        }

        fun vish(v: View, bb: Boolean = true) {
            v.visibility = if (bb) View.VISIBLE else View.INVISIBLE
        }

        fun openProfile(c: AppCompatActivity, user: String) {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/%s/".format(user)))
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

        fun date(time: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = time }
            return "${cal[Calendar.YEAR]}.${z(cal[Calendar.MONTH] + 1)}." +
                    "${z(cal[Calendar.DAY_OF_MONTH])} - ${z(cal[Calendar.HOUR_OF_DAY])}:" +
                    "${z(cal[Calendar.MINUTE])}:${z(cal[Calendar.SECOND])}"
        }
    }
}
