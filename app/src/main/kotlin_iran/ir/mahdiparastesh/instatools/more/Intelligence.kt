package ir.mahdiparastesh.instatools.more

object Intelligence : BaseIntel() {
    override fun onLaunch(c: BaseActivity): Boolean {
        if (!super.onLaunch(c)) return false
        if (shallCollect() || iranCensor) collectData()
        return true
    }
    // Myket and Bazaar don't test app updates!!
}
