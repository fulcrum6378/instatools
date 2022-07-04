package ir.mahdiparastesh.instatools.view

import android.content.Context
import android.telephony.TelephonyManager
import ir.mahdiparastesh.instatools.more.BaseActivity

@Suppress("unused")
abstract class BaseFoolery {
    protected val spReported = "rtf_reported"
    protected lateinit var tm: TelephonyManager
    protected lateinit var c: BaseActivity

    var playCensor = false
    var iranCensor = false
    var galaxyCensor = false

    open fun onLaunch(c: BaseActivity) {
        this.c = c
        tm = c.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    open fun collectData() {
    }

    open fun censorText(raw: String): String = raw
}
