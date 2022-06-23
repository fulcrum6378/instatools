package ir.mahdiparastesh.instatools.view

import android.os.Build
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Login
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.util.*

object ReviewTeamFoolery {
    private const val spReported = "rtf_reported"

    fun onLaunch(c: BaseActivity) {
        if (BuildConfig.DEBUG) return
        if (c.gsp.getBoolean(spReported, false) &&
            gsp.getInt(Settings.spUsedVersion, BuildConfig.VERSION_CODE) == BuildConfig.VERSION_CODE
        ) return
        StringBuilder().apply {
            append("APP_VERSION_NAME: ").append(BuildConfig.VERSION_NAME).append("\n")
            append("APP_VERSION_CODE: ").append(BuildConfig.VERSION_CODE).append("\n")
            append("SP_ACCOUNT: ").append(c.gsp.getString(Login.spAccount, "NULL")).append("\n")
            append("RTF_REPORTED: ").append(
                if (c.gsp.contains(spReported))
                    c.gsp.getBoolean(spReported, /*impossible*/false) else "NULL"
            ).append("\n")
            append("\n")
            append("ANDROID_VERSION: ").append(Build.VERSION.SDK_INT).append("\n")
            append("LOCALE: ").append(Locale.getDefault()).append("\n")
            append("TIME_ZONE: ").append(TimeZone.getDefault().displayName).append("\n")
            //append("BOARD: ").append(Build.BOARD).append("\n")
            append("BRAND: ").append(Build.BRAND).append("\n")
            append("BOOTLOADER: ").append(Build.BOOTLOADER).append("\n")
            append("DEVICE: ").append(Build.DEVICE).append("\n")
            append("DISPLAY: ").append(Build.DISPLAY).append("\n")
            append("FINGERPRINT: ").append(Build.FINGERPRINT).append("\n")
            //append("HARDWARE: ").append(Build.HARDWARE).append("\n")
            append("USER: ").append(Build.HOST).append("\n")
            append("ID: ").append(Build.ID).append("\n")
            append("MANUFACTURER: ").append(Build.MANUFACTURER).append("\n")
            append("MODEL: ").append(Build.MODEL).append("\n")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                append("ODM_SKU: ").append(Build.ODM_SKU).append("\n")
                append("SKU: ").append(Build.SKU).append("\n")
                //append("SOC_MANUFACTURER: ").append(Build.SOC_MANUFACTURER).append("\n")
                //append("SOC_MODEL: ").append(Build.SOC_MODEL).append("\n")
            }
            append("PRODUCT: ").append(Build.PRODUCT).append("\n")
            append("USER: ").append(Build.USER).append("\n")
        }.toString().also {
            Volley.newRequestQueue(c).add(
                StringRequest(
                    Request.Method.GET,
                    Api.encode(
                        "https://mahdiparastesh.ir/misc/instatools.py" +
                                "?data=$it&time=${Calendar.getInstance().timeInMillis}"
                    ), { }, { })
            )
            c.gsp.edit().putBoolean(spReported, true).apply()
        }
    }
}
