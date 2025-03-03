package ir.mahdiparastesh.instatools.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.view.forEach
import androidx.core.view.get
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.internal.BaselineLayout
import com.google.android.material.snackbar.Snackbar
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.R
import java.util.*

object UiTools {
    const val PROFILE = "https://www.instagram.com/%s/"
    const val IG_OPENABLE = "https://www.instagram.com/"
    const val INSTA_PACKAGE = "com.instagram.android"
    const val MAX_BADGE_CHAR = 6
    //private const val maxInaccurateTimeItems = 2

    val accFromUrl = arrayOf(Login.RAW_HOST, Login.HOST)
    val materialTheme = com.google.android.material.R.style.Theme_MaterialComponents_DayNight
    val reqPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf()

    /** Gets the IG user name from a link. */
    fun accountFromUrl(str: String, host: String): String? =
        if (str.startsWith(host)) str.substringAfter(host).substringBefore("/")
            .substringBefore("?") else null

    /** @return TextView instances of a BottomNavigationView for applying custom styles on them. */
    @SuppressLint("RestrictedApi")
    fun bnvTitles(bnv: BottomNavigationView): List<TextView> {
        val list = ArrayList<TextView>()
        (bnv[0] as BottomNavigationMenuView).forEach {
            val item = it as BottomNavigationItemView
            // 0 => FrameLayout (icon) and 1 => BaselineLayout (title)
            val title = item[1] as BaselineLayout // has 2 AppCompatTextView
            list.add(title[0] as TextView) // normal state
            list.add(title[1] as TextView) // selected state
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
    fun openProfile(c: Activity, user: String): Boolean = try {
        c.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PROFILE.format(user)))
                .setPackage(INSTA_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
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

    /** Helper class for vibrations of any duration. */
    @Suppress("DEPRECATION")
    fun Context.shake(dur: Long = 48L) {
        val vib = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        vib.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Makes an easily readable datetime. */
    /*fun Context.inaccurateTime(milliseconds: Long, zeroIfNothing: Boolean = false): String {
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
                    pairs.removeAt(0)
                else break
            if (pairs.size > maxInaccurateTimeItems)
                pairs = pairs.subList(0, maxInaccurateTimeItems)
            while (pairs.isNotEmpty())
                if (pairs.last().second == 0)
                    pairs.removeAt(pairs.lastIndex)
                else break
        }
        return pairs.joinToString(getString(R.string.inaccurateTimeSep)) { it.first.format(it.second) }
    }*/

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

    /** @return a colour from a theme using an attribute resource. */
    @ColorInt
    fun ContextThemeWrapper.themeColor(@AttrRes attr: Int = android.R.attr.colorAccent) =
        TypedValue().apply {
            theme.resolveAttribute(attr, this, true)
        }.data

    /** Helper function for showing a Snackbar. */
    fun snackbar(view: View, text: String, anchor: View? = null, dur: Int = Snackbar.LENGTH_LONG) {
        try {
            Snackbar.make(
                ContextThemeWrapper(view.context, R.style.Theme_InstaTools_Snackbar),
                view, text, dur
            ).setAnchorView(anchor).setTextMaxLines(5).show()
        } catch (_: IllegalArgumentException) {
            // No suitable parent found from the given view. Please provide a valid view.
        }
    }

    /** Helper function for showing a Snackbar. */
    fun snackbar(
        view: View, @StringRes res: Int, anchor: View? = null, dur: Int = Snackbar.LENGTH_LONG
    ) {
        snackbar(view, view.context.getString(res), anchor, dur)
    }

    fun apiError(c: Context, code: Int): String = c.resources.getString(
        when (code) {
            -1 -> R.string.noInternet
            -2 -> R.string.connectionFailure
            -3 -> R.string.connectionBroken
            -4 -> R.string.loggedOut
            -5 -> R.string.operationFailed
            401 -> R.string.loggedOut401
            404 -> R.string.notFound
            429 -> R.string.manyRequests
            else -> R.string.httpError
        }, code
    )

    /*fun urlEncode(uriString: String?): String? {
        if (uriString == null) return null
        if (TextUtils.isEmpty(uriString)) return uriString
        val allowedUrlCharacters = Pattern.compile(
            "([A-Za-z\\d_.~:/?#\\[\\]@!$&'()*+,;" + "=-]|%[\\da-fA-F]{2})+"
        )
        val matcher = allowedUrlCharacters.matcher(uriString)
        var validUri: String? = null
        if (matcher.find()) validUri = matcher.group()
        if (TextUtils.isEmpty(validUri) || uriString.length == validUri!!.length)
            return uriString

        val uri = Uri.parse(uriString)
        val uriBuilder = Uri.Builder().scheme(uri.scheme).authority(uri.authority)
        for (path in uri.pathSegments) uriBuilder.appendPath(path)
        for (key in uri.queryParameterNames)
            uriBuilder.appendQueryParameter(key, uri.getQueryParameter(key))
        return uriBuilder.build().toString()
    }*/
}
