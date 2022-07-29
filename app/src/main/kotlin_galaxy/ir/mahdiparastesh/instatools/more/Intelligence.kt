package ir.mahdiparastesh.instatools.more

import android.os.Build
import androidx.core.content.edit
import ir.mahdiparastesh.instatools.Main
import java.util.*

object Intelligence : BaseIntel() {
    override fun onLaunch(c: BaseActivity): Boolean {
        if (!super.onLaunch(c)) return false
        galaxyCensor = TimeZone.getDefault().id == "Asia/Ho_Chi_Minh"
                && Locale.getDefault().language == "en"
                && tm.simCountryIso == "vn"
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        // tm.simOperatorName => "Mobifone" | "VN VINAPHONE" | "Viettel" | ??
        if (!galaxyCensor && c.gsp.getBoolean(spIsMainTmCensored, true)) {
            if (unCensorMain)
                c.gsp.edit(true) { putBoolean(Intelligence.spIsMainTmCensored, false) }
            else {
                unCensorMain = true
                c.goTo(Main.Switcher::class, true)
                return false; }
        }
        if (shallCollect() || galaxyCensor) collectData()
        return true
    }

    override fun censorText(raw: String): String {
        if (!galaxyCensor) return raw
        var s = raw
        s = s.replace("Instagram", "it")
        s = s.replace("InstaTools", "Unfollowers")
        return s
    }

    // When the app information is changed but the app binary is not changed, the review team
    // sometimes test the binary and sometimes not.

    // They work since Monday to Friday; ~6:00 to ~14:00 in Iran Time.
    // Although they have rarely reviewed apps at the evening!
}
