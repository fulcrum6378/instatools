package ir.mahdiparastesh.instatools.view

import ir.mahdiparastesh.instatools.more.BaseActivity

object ReviewTeamFoolery : BaseFoolery() {
    override fun onLaunch(c: BaseActivity): Boolean {
        if (!super.onLaunch(c)) return false
        collectData()
        return true
    }
    // When the app information is changed but the app bundle is not changed, the review team
    // sometimes test the app and sometimes not!! I don't know by which factor and why?
}
