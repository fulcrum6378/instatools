package ir.mahdiparastesh.instatools.view

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Html
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.RadioGroup
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.forEach
import androidx.core.view.get
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.internal.BaselineLayout
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.json.GraphQl
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.util.*
import kotlin.math.abs

object UiTools {
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
    const val MP = "https://mahdiparastesh.ir/"
    const val APP_NAME = "InstaTools"
    val ACC_FROM_URL = arrayOf(Login.rawHost, Login.host)
    private const val maxInaccurateTimeItems = 2
    val materialTheme = com.google.android.material.R.style.Theme_MaterialComponents_DayNight
    const val MAX_BADGE_CHAR = 6
    private const val OPTION_DISABLED_ALPHA = 0.5f
    val reqPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf()

    /** @return TextView instances of a BottomNavigationView for applying custom styles on them. */
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

    /** Opens an IG profile in Instagram, if Instagram is installed. */
    fun openProfile(c: Activity, user: String) {
        try {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(PROFILE.format(user)))
                    .setPackage(INSTA_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Opens a link without any specifications on which app should handle it. */
    fun openLink(c: Activity, link: String) {
        try {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Helper class for turning 1 to "01". */
    fun z(n: Int): String {
        val s = n.toString()
        return if (s.length == 1) "0$s" else s
    }

    /** Helper class for vibrations of any duration. */
    @Suppress("DEPRECATION")
    fun Context.shake(dur: Long = 48L) {
        val vib = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vib.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
        else vib.vibrate(dur)
    }

    /** Converts a timestamp to a human-readable date. */
    fun date(time: Long): String {
        val cal = time.calendar()
        return "${cal[Calendar.YEAR]}.${z(cal[Calendar.MONTH] + 1)}." +
            "${z(cal[Calendar.DAY_OF_MONTH])} - ${z(cal[Calendar.HOUR_OF_DAY])}:" +
            "${z(cal[Calendar.MINUTE])}:${z(cal[Calendar.SECOND])}"
    }

    /** Linkifies an AppCompatTextView. */
    @Suppress("DEPRECATION")
    fun AppCompatTextView.anchor(text: String?, url: String?) {
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

    /** Converts a timestamp to a Calendar instance. */
    fun Long.calendar(): Calendar = // needs milliseconds
        Calendar.getInstance().apply { timeInMillis = this@calendar }

    /** Converts a microseconds timestamp to a milliseconds one. */
    fun Double.xFromMicroseconds() = toLong() / 1000L

    /** Converts a seconds timestamp to a milliseconds one. */
    fun Double.xFromSeconds() = toLong() * 1000L

    /** Gets the IG user name from a link. */
    fun String.accFromUrl(host: String): String? =
        if (startsWith(host)) substringAfter(host).substringBefore("/")
            .substringBefore("?") else null

    /** Position of a "Jump to Top" button. */
    fun jumperTrans(c: BaseActivity) = (c.resources.getDimension(R.dimen.jumperSize) +
        c.resources.getDimension(R.dimen.jumperBottom)) * 1.25f

    /** Animation for a "Jump to Top" button. */
    fun anJumper(c: BaseActivity, jumper: View, bb: Boolean): ObjectAnimator =
        ObjectAnimator.ofFloat(
            jumper, View.TRANSLATION_Y, if (bb) 0f else jumperTrans(c)
        ).apply {
            duration = 500L
            interpolator = OvershootInterpolator(1.75f)
            start()
        }

    /** Opens a Direct Message in Instagram. */
    @Suppress("SpellCheckingInspection")
    fun openDm(c: Activity, threadId: String) {
        try {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("ig://direct_v2?id=$threadId")).setComponent(
                    ComponentName(INSTA_PACKAGE, "com.instagram.mainactivity.MainActivity")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Makes an easily readable datetime. */
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

    /** Explains bytes for humans. */
    fun Context.showBytes(length: Long): String {
        val units = resources.getStringArray(R.array.bytes)
        var unit = 0
        var nominalSize = length.toDouble()
        while ((nominalSize / 1024.0) > 1.0) {
            nominalSize /= 1024.0
            unit++
            if (unit == units.size - 1) break
        }
        return units[unit].format(nominalSize.toInt())
    }

    /** @return a datetime text to be used in a file name. */
    fun fileDateTime(time: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = time }
        return "${cal[Calendar.YEAR]}${z(cal[Calendar.MONTH] + 1)}" +
            "${z(cal[Calendar.DAY_OF_MONTH])}_${z(cal[Calendar.HOUR_OF_DAY])}" +
            "${z(cal[Calendar.MINUTE])}${z(cal[Calendar.SECOND])}"
    }

    /** @return a colour from a theme using an attribute resource. */
    @ColorInt
    fun ContextThemeWrapper.themeColor(@AttrRes attr: Int = android.R.attr.colorAccent) =
        TypedValue().apply {
            theme.resolveAttribute(attr, this, true)
        }.data

    /** Enables or disables all RadioGroup items and sets an alpha value for each. */
    fun RadioGroup.areEnabled(bb: Boolean) = forEach {
        it.isEnabled = bb
        it.alpha = if (bb) 1f else OPTION_DISABLED_ALPHA
    }

    /** Enables or disables a CompoundButton and sets an alpha value for it. */
    fun CompoundButton.enabled(bb: Boolean) {
        isEnabled = bb
        alpha = if (bb) 1f else OPTION_DISABLED_ALPHA
    }

    /** Finds a thumbnail address from a GraphQl.Post. */
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

    /** Helper function for showing a Snackbar. */
    fun snackbar(view: View, text: String, dur: Int, anchor: View? = null) {
        try {
            Snackbar.make(
                ContextThemeWrapper(view.context, R.style.Theme_InstaTools_Snackbar),
                view, text, dur
            ).setAnchorView(anchor).setTextMaxLines(5).show()
        } catch (ignored: IllegalArgumentException) {
            // No suitable parent found from the given view. Please provide a valid view.
        }
    }

    /** Helper function for showing a Snackbar. */
    fun snackbar(view: View, @StringRes res: Int, dur: Int, anchor: View? = null) {
        snackbar(view, view.context.getString(res), dur, anchor)
    }

    /** Rounds a Bitmap as a circle. */
    fun bmpRound(bmp: Bitmap): Bitmap =
        Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawRoundRect(
                RectF(Rect(0, 0, bmp.width, bmp.height)),
                bmp.width / 2f, bmp.height / 2f,
                Paint().apply { flags = Paint.ANTI_ALIAS_FLAG })
            val paintImage =
                Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
            canvas.drawBitmap(bmp, 0f, 0f, paintImage)
        }

    /** Helper function for setting Glide target of an IG profile. */
    fun targetProfile(iv: ImageView) = object : CustomTarget<Bitmap>() {
        override fun onLoadCleared(placeholder: Drawable?) {
            iv.setImageDrawable(null)
        }

        override fun onResourceReady(res: Bitmap, trans: Transition<in Bitmap>?) {
            iv.setImageBitmap(bmpRound(res))
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            super.onLoadFailed(errorDrawable)
            iv.setImageDrawable(null)
        }
    }
}
