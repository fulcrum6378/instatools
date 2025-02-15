package ir.mahdiparastesh.instatools.more

/**
 * Subclass of Thread with a boolean field named "active" which indicates whether the thread is
 * still working or not.
 */
abstract class BaseThread : Thread() {
    var active = false

    override fun run() {
        active = true
    }

    override fun interrupt() {
        if (!active) return
        active = false
        super.interrupt()
    }
}
