package ir.mahdiparastesh.instatools.more

open class BaseThread : Thread() {
    var active = false

    override fun run() {
        active = true
    }

    override fun interrupt() {
        active = false
        super.interrupt()
    }
}
