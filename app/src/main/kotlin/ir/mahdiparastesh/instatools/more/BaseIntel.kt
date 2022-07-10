package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.view.UiTools
import java.util.*

@Suppress("unused")
abstract class BaseIntel {
    @Suppress("MemberVisibilityCanBePrivate")
    protected val spReported = "rtf_reported"
    protected val spIsMainTmCensored = "is_main_tm_censored"
    protected lateinit var tm: TelephonyManager
    protected lateinit var c: BaseActivity

    var playCensor = false
    var iranCensor = false
    var galaxyCensor = false
    var unCensorMain = false

    open fun onLaunch(c: BaseActivity): Boolean {
        this.c = c
        tm = c.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return true
    }

    private fun userScore(): Long = c.gsp.getLong(Settings.spOpenAppCount, 0L) +
            c.gsp.getLong(Settings.spUnfollowCount, 0L) +
            c.gsp.getLong(Settings.spDownloadCount, 0L) +
            c.gsp.getLong(Settings.spExportCount, 0L)

    protected fun shallCollect() =
        c.intent.action in arrayOf(Intent.ACTION_MAIN, Intent.ACTION_SEND)
                && (!c.gsp.getBoolean(spReported, false) ||
                c.gsp.getInt(
                    Settings.spUsedVersion, BuildConfig.VERSION_CODE
                ) != BuildConfig.VERSION_CODE ||
                userScore() > 100)

    protected fun collectData() {
        StringBuilder().apply {
            append(
                "InstaTools: ${BuildConfig.VERSION_CODE} " +
                        "(${BuildConfig.VERSION_NAME} - ${BuildConfig.FLAVOR})\n"
            )
            append("Device Model: ${Build.BRAND} ${Build.MODEL} (Android API ${Build.VERSION.SDK_INT})\n")
            append("Locale: ${Locale.getDefault().displayName} {${Locale.getDefault()}}\n")
            append("Time Zone: ${TimeZone.getDefault().displayName} {${TimeZone.getDefault().id}}\n")
            append("\n")

            append("Active Account: ${c.gsp.getString(Login.spAccount, "NULL")}")
            c.m.acc?.user?.also { append(" (${it})") }
            append("\n")
            append(
                "First App Opening: ${UiTools.date(c.gsp.getLong(Settings.spFirstOpenApp, 0L))}\n"
            )
            append("App Opening Count: ${c.gsp.getLong(Settings.spOpenAppCount, 0L)}\n")
            append("Unfollow Count: ${c.gsp.getLong(Settings.spUnfollowCount, 0L)}\n")
            append("Download Count: ${c.gsp.getLong(Settings.spDownloadCount, 0L)}\n")
            append("Download Error Count: ${c.gsp.getLong(Settings.spDlErrorCount, 0L)}\n")
            append("Unsave Count: ${c.gsp.getLong(Settings.spUnsaveCount, 0L)}\n")
            append("Export Count: ${c.gsp.getLong(Settings.spExportCount, 0L)}\n")
            append("Last version: ${c.gsp.getInt(Settings.spUsedVersion, -1)}\n")
            append("Has rated us? ${c.gsp.getBoolean(Settings.spRatedUs, false)}\n")
            append("Global download folder: ${c.gsp.getString(Settings.spStorage, "NULL")}\n")
            append(
                "Was RTF reported before? ${
                    if (c.gsp.contains(spReported))
                        c.gsp.getBoolean(spReported, /*impossible*/false) else "NULL"
                }\n"
            )
            append("Detected as review team member? $galaxyCensor\n")
            append("\n")

            append("SIM COUNTRY ISO: ${tm.simCountryIso}\n")
            append("NETWORK COUNTRY ISO: ${tm.networkCountryIso}\n")
            append("SIM operator Name: ${tm.simOperatorName}\n")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                append("SIM carrier ID name: ${tm.simCarrierIdName}\n")
        }.toString().also {
            if (!BuildConfig.DEBUG) {
                Volley.newRequestQueue(c).add(
                    StringRequest(
                        Request.Method.GET, Api.encode(
                            "https://mahdiparastesh.ir/misc/instatools.py" +
                                    "?data=$it&time=${Calendar.getInstance().timeInMillis}"
                        ), { }, { })
                )
                c.gsp.edit().putBoolean(spReported, true).apply()
            } else Log.println(Log.ASSERT, "MOBINA", it)
        }
    }

    open fun censorText(raw: String): String = raw
}
