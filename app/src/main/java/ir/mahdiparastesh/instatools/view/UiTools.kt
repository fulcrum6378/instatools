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
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.forEach
import androidx.core.view.get
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.initialization.AdapterStatus
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.internal.BaselineLayout
import com.google.android.play.core.review.ReviewManagerFactory
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.MassFollower.Companion.rewardAccountForFollower
import ir.mahdiparastesh.instatools.json.GraphQl
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.Persistent
import java.util.*
import kotlin.math.abs

class UiTools {
    companion object {
        const val DATE_FORMAT = "yyyy.MM.dd"
        const val TIME_FORMAT = "hh:mm:ss"
        const val PROFILE = "https://www.instagram.com/%s/"
        const val POST_LINK = "https://www.instagram.com/p/%s/"
        const val STORY_LINK = "https://www.instagram.com/stories/%1\$s/%2\$s"
        const val HIGHLIGHT_LINK = "https://www.instagram.com/stories/highlights/%s/" // inexact
        const val REEL_LINK = "https://www.instagram.com/reel/%s/"
        const val IGTV_LINK = "https://www.instagram.com/tv/%s/"
        const val IG_OPENABLE = "https://www.instagram.com/"
        const val INSTA_PACKAGE = "com.instagram.android"
        private const val ADMOB = "com.google.android.gms.ads.MobileAds"
        val ACC_FROM_URL = arrayOf(Login.rawHost, Login.host)
        private const val maxInaccurateTimeItems = 2
        val materialTheme = com.google.android.material.R.style.Theme_MaterialComponents_DayNight
        const val MAX_BADGE_CHAR = 5

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

        fun adaptiveBanner(c: BaseActivity, @StringRes unitId: Int) = AdView(c).apply {
            id = R.id.adBanner
            setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    c, (c.dm.widthPixels / c.dm.density).toInt()
                )
            )
            adUnitId = c.getString(unitId)
        }

        fun adaptiveBannerLp() = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID }

        fun AlertDialog.stylise(c: BaseActivity): AlertDialog {
            // Don't move this function to BaseActivity
            val ca = c.themeColor()
            window?.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
                typeface = c.fontBold
                setTextColor(ca)
            }
            window?.findViewById<TextView>(android.R.id.message)?.apply {
                typeface = c.fontRegular
                //setTextColor(ca)
            }
            getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                typeface = c.fontRegular
                setTextColor(ca)
            }
            getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                typeface = c.fontRegular
                setTextColor(ca)
            }
            getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
                typeface = c.fontRegular
                setTextColor(ca)
            }
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
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

        fun Context.showBytes(length: Long): String {
            val units = resources.getStringArray(R.array.bytes)
            var unit = 0
            var nominalSize = length
            while ((nominalSize / 1024L) > 1) {
                nominalSize /= 1024L
                unit++
                if (unit == units.size - 1) break
            }
            return units[unit].format(nominalSize)
        }

        fun InitializationStatus.isReady(): Boolean = if (adapterStatusMap.containsKey(ADMOB))
            adapterStatusMap[ADMOB]?.initializationState == AdapterStatus.State.READY
        else false

        fun fileDateTime(time: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = time }
            return "${cal[Calendar.YEAR]}${z(cal[Calendar.MONTH] + 1)}" +
                    "${z(cal[Calendar.DAY_OF_MONTH])}_${z(cal[Calendar.HOUR_OF_DAY])}" +
                    "${z(cal[Calendar.MINUTE])}${z(cal[Calendar.SECOND])}"
        }

        @ColorInt
        fun ContextThemeWrapper.themeColor(@AttrRes attr: Int = android.R.attr.colorAccent) =
            TypedValue().apply {
                theme.resolveAttribute(attr, this, true)
            }.data

        fun RadioGroup.areEnabled(bb: Boolean) = forEach { it.isEnabled = bb }

        fun GraphQl.Post.thumb(nearest: Double = 0.0): String {
            if (thumbnail_resources == null) return thumbnail_src
            var selected: GraphQl.Src? = null
            for (src in thumbnail_resources)
                if (selected == null)
                    selected = src
                else if (abs(selected.config_width - nearest) < abs(src.config_width - nearest))
                    selected = src
            return selected?.src ?: thumbnail_src
        }

        fun hasReviewedApp(c: Persistent) = c.gsp.getBoolean(Settings.spRatedUs, false)

        fun reviewApp(
            c: BaseActivity,
            reward: Int = MassFollower.RATE_US_UNINTENTIONALLY_UNLOCK_TIMES,
            onReqSuccess: () -> Unit = {},
            onReqComplete: () -> Unit = {},
            onDone: () -> Unit = {}
        ) {
            val reviewManager = ReviewManagerFactory.create(c) // FakeReviewManager(c)
            reviewManager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onReqSuccess()
                    reviewManager.launchReviewFlow(c, task.result).addOnCompleteListener {
                        onDone()
                        c.rewardAccountForFollower(reward)
                        c.gsp.edit().putBoolean(Settings.spRatedUs, true).apply()
                    }
                } else if (BuildConfig.DEBUG) task.exception?.let { throw it }
                onReqComplete()
            }
        }
    }
}
