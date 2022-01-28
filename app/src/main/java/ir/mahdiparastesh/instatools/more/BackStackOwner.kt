package ir.mahdiparastesh.instatools.more

interface BackStackOwner {
    fun goBack(): Boolean {
        return false
    }
}
