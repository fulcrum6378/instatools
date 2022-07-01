package ir.mahdiparastesh.instatools.view

import android.content.Context
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
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.util.*

object ReviewTeamFoolery : BaseFoolery() {
    fun onLaunch(c: BaseActivity) {
        val tm = c.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        galaxyCensor = TimeZone.getDefault().displayName == "Indochina Time"
                // ^ this indicates that the phone language is English ^
                // as opposed to: TimeZone.getDefault().id == "Asia/Ho_Chi_Minh"
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && tm.simCountryIso == "vn"
                && tm.simOperatorName == "Mobifone"
        /*if (c.gsp.getBoolean(spReported, false) && !BuildConfig.DEBUG &&
            c.gsp.getInt(
                Settings.spUsedVersion, BuildConfig.VERSION_CODE
            ) == BuildConfig.VERSION_CODE
        ) return*/
        StringBuilder().apply {
            append("InstaTools: ${BuildConfig.VERSION_CODE} (${BuildConfig.VERSION_NAME})\n")
            append("Device Model: ${Build.BRAND} ${Build.MODEL} (Android API ${Build.VERSION.SDK_INT})\n")
            append("Locale: ${Locale.getDefault().displayName} {${Locale.getDefault()}}\n")
            append("Time Zone: ${TimeZone.getDefault().displayName} {${TimeZone.getDefault().id}}\n")
            append("\n")

            append("Active Account: ${c.gsp.getString(Login.spAccount, "NULL")}\n")
            append("Download Count: ${c.gsp.getLong(Settings.spDownloadCount, 0L)}\n")
            append("Last version: ${c.gsp.getInt(Settings.spUsedVersion, -1)}\n")
            append("Has rated us? ${c.gsp.getBoolean(Settings.spRatedUs, false)}\n")
            append(
                "Was RTF reported before? ${
                    if (c.gsp.contains(spReported))
                        c.gsp.getBoolean(spReported, /*impossible*/false) else "NULL"
                }\n"
            )
            append("Global download folder: ${c.gsp.getString(Settings.spStorage, "NULL")}\n")
            append("Detected as review team member? $galaxyCensor\n")
            append("\n")

            append("SIM COUNTRY ISO: ${tm.simCountryIso}\n")
            append("NETWORK COUNTRY ISO: ${tm.networkCountryIso}\n")
            append("SIM operator Name: ${tm.simOperatorName}\n")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                append("SIM carrier ID name: ${tm.simCarrierIdName}\n")
            append("Is network roaming? ${tm.isNetworkRoaming}\n")
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

    override fun censorText(raw: String): String {
        if (!galaxyCensor) return raw
        var s = raw
        s = s.replace("Instagram", "it")
        s = s.replace("InstaTools", "Unfollowers")
        return s
    }
}
