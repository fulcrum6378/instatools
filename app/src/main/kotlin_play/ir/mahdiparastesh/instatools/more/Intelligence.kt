package ir.mahdiparastesh.instatools.more

object Intelligence : BaseIntel() {
    override fun onLaunch(c: BaseActivity): Boolean {
        if (!super.onLaunch(c)) return false
        if (shallCollect() || playCensor) collectData()
        return true
    }
    // When the app information is changed but the app bundle is not changed, the review team
    // sometimes test the app and sometimes not!! I don't know by which factor and why?
}
