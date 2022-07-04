package ir.mahdiparastesh.instatools.view

import ir.mahdiparastesh.instatools.more.BaseActivity

object ReviewTeamFoolery : BaseFoolery() {
    override fun onLaunch(c: BaseActivity): Boolean {
        // Myket doesn't test app updates!!
        return true
    }
}
