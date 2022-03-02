package ir.mahdiparastesh.instatools.view

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
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
import android.text.method.LinkMovementMethod
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
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class UiTools {
    companion object {
        const val PROFILE = "https://www.instagram.com/%s/"
        val ACC_FROM_URL = arrayOf(Login.rawHost, Login.host)

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
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROFILE.format(user))))
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
            // INSTAGRAM WAS INSTALLED YET....
            /*android.util.AndroidRuntimeException: Calling startActivity() from outside of an Activity  context requires the FLAG_ACTIVITY_NEW_TASK flag. Is this really what you want?
        at android.app.ContextImpl.startActivity(ContextImpl.java:1682)
        at android.app.ContextImpl.startActivity(ContextImpl.java:1669)
        at android.content.ContextWrapper.startActivity(ContextWrapper.java:338)
        at android.content.ContextWrapper.startActivity(ContextWrapper.java:338)
        at android.text.style.URLSpan.onClick(URLSpan.java:72)
        at android.text.method.LinkMovementMethod.onTouchEvent(LinkMovementMethod.java:217)
        at android.widget.TextView.onTouchEvent(TextView.java:9646)*/
        }

        fun calendar(unix: Long): Calendar =
            Calendar.getInstance().apply { timeInMillis = unix }

        fun calendar(unix: Double): Calendar =
            Calendar.getInstance().apply { timeInMillis = instaTime(unix) }

        fun instaTime(time: Double) = time.toLong() / 1000L

        fun adaptiveBanner(c: BaseActivity, unitId: String) = AdView(c).apply {
            id = R.id.adBanner
            adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                c, (c.dm.widthPixels / c.dm.density).toInt()
            )
            adUnitId = if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/6300978111" else unitId
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
                Intent(Intent.ACTION_VIEW, Uri.parse("ig://direct_v2?id=$threadId")).apply {
                    component = ComponentName(
                        "com.instagram.android",
                        "com.instagram.mainactivity.MainActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
        }

        fun TextView.bolden(c: BaseActivity, font: Typeface = c.fontBold) {
            if (!c.shallBolden()) typeface = font
            else setTypeface(font, Typeface.BOLD)
        }
    }
}
