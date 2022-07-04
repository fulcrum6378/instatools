package ir.mahdiparastesh.instatools.view

import android.content.Context
import android.telephony.TelephonyManager
import ir.mahdiparastesh.instatools.more.BaseActivity

@Suppress("unused")
abstract class BaseFoolery {
    protected val spReported = "rtf_reported"
    val spIsMainTmCensored = "is_main_tm_censored"
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

    open fun collectData() {
    }

    open fun censorText(raw: String): String = raw
}
