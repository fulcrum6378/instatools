package ir.mahdiparastesh.instatools.view

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.forEach
import androidx.core.view.get
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.internal.BaselineLayout
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.util.*

class UiTools {
    companion object {
        const val PROFILE = "https://www.instagram.com/%s/"
        const val POST_LINK = "https://www.instagram.com/p/%s/"
        const val INSTA_PACKAGE = "com.instagram.android"
        val ACC_FROM_URL = arrayOf(Login.rawHost, Login.host)
        private const val maxInaccurateTimeItems = 2

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

        fun openProfile(c: Activity, user: String) {
            try {
                c.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(PROFILE.format(user)))
                        .setPackage(INSTA_PACKAGE)
                    //.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: ActivityNotFoundException) {
            }
        }

        fun z(n: Int): String {
            val s = n.toString()
            return if (s.length == 1) "0$s" else s
        }

        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        fun Context.shake(dur: Long = 48L) {
            val vib = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                vib.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
            else vib.vibrate(dur)
        }

        fun date(time: Long): String {
            val cal = time.calendar()
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
            movementMethod = SafeLinkMovementMethod.getInstance()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                setText(Html.fromHtml("<a href=\"$url\">$text</a>", Html.FROM_HTML_MODE_LEGACY))
            else setText(Html.fromHtml("<a href=\"$url\">$text</a>"))
        }

        fun Long.calendar(): Calendar = // needs milliseconds
            Calendar.getInstance().apply { timeInMillis = this@calendar }

        fun Double.xFromMicroseconds() = toLong() / 1000L

        fun Double.xFromSeconds() = toLong() * 1000L

        fun adaptiveBanner(c: BaseActivity, unitId: String) = AdView(c).apply {
            id = R.id.adBanner
            adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                c, (c.dm.widthPixels / c.dm.density).toInt()
            )
            adUnitId = unitId
        }

        fun adaptiveBannerLp() = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID }

        fun AlertDialog.stylise(c: BaseActivity): AlertDialog {
            // If you move this function to BaseActivity, Fragments won't be able to provide "c".
            // "ownerActivity" is always null though, even after calling Builder.show().
            window?.findViewById<TextView>(R.id.alertTitle)
                ?.apply { typeface = c.fontBold }
            window?.findViewById<TextView>(android.R.id.message)
                ?.apply { typeface = c.fontRegular }
            getButton(AlertDialog.BUTTON_POSITIVE)?.apply { typeface = c.fontRegular }
            getButton(AlertDialog.BUTTON_NEGATIVE)?.apply { typeface = c.fontRegular }
            getButton(AlertDialog.BUTTON_NEUTRAL)?.apply { typeface = c.fontRegular }
            return this
        }

        fun String.accFromUrl(host: String): String? =
            if (startsWith(host)) substringAfter(host).substringBefore("/")
                .substringBefore("?") else null

        fun jumperTrans(c: BaseActivity) = (c.resources.getDimension(R.dimen.jumperSize) +
                c.resources.getDimension(R.dimen.jumperBottom)) * 1.25f

        fun anJumper(c: BaseActivity, jumper: View, bb: Boolean): ObjectAnimator =
            ObjectAnimator.ofFloat(
                jumper, View.TRANSLATION_Y, if (bb) 0f else jumperTrans(c)
            ).apply {
                duration = 500L
                interpolator = OvershootInterpolator(1.75f)
                start()
            }

        @Suppress("SpellCheckingInspection")
        fun openDm(c: Activity, threadId: String) {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("ig://direct_v2?id=$threadId")).setComponent(
                    ComponentName(INSTA_PACKAGE, "com.instagram.mainactivity.MainActivity")
                ) //.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        fun TextView.bolden(c: BaseActivity, font: Typeface = c.fontBold) {
            if (!c.shallBolden) typeface = font
            else setTypeface(font, Typeface.BOLD)
        }

        fun Context.inaccurateTime(milliseconds: Long, zeroIfNothing: Boolean = false): String {
            var shrinking = milliseconds / 1000L
            val mon = (shrinking / 2592000L).toInt()
            shrinking -= mon * 86400L
            val wek = (shrinking / 604800L).toInt()
            shrinking -= wek * 604800L
            val day = (shrinking / 86400L).toInt()
            shrinking -= day * 86400L
            val hor = (shrinking / 3600L).toInt()
            shrinking -= hor * 3600L
            val mns = (shrinking / 60L).toInt()
            shrinking -= mns * 60L
            val sex = shrinking.toInt()

            val arr = resources.getStringArray(R.array.inaccurateTime)
            var pairs = arrayOf(mon, wek, day, hor, mns, sex)
                .mapIndexed { index, i -> arr[index] to i }.toMutableList()
            if (pairs.all { it.second == 0 } && zeroIfNothing)
                pairs = arrayListOf(pairs.last())
            else {
                while (pairs.isNotEmpty())
                    if (pairs.first().second == 0)
                        pairs.removeFirst()
                    else break
                if (pairs.size > maxInaccurateTimeItems)
                    pairs = pairs.subList(0, maxInaccurateTimeItems)
                while (pairs.isNotEmpty())
                    if (pairs.last().second == 0)
                        pairs.removeLast()
                    else break
            }
            return pairs.joinToString(getString(R.string.inaccurateTimeSep)) { it.first.format(it.second) }
        }
    }
}
