package ir.mahdiparastesh.instatools.more

import android.view.MenuItem
import ir.mahdiparastesh.instatools.R

abstract class ServiceOwnerActivity : BaseActivity() {
    abstract val controllerId: Int
    private var lastEmptinessState = true

    private val controller: MenuItem? get() = toolbar.menu.findItem(controllerId)

    fun updateControlButton(it: Boolean) {
        controller?.apply {
            setIcon(if (it) R.drawable.pause else R.drawable.play)
            setTitle(if (it) R.string.stop else R.string.start)
        }
    }

    fun updateIfEmpty(isEmpty: Boolean, payThePrice: () -> Unit = {}) {
        val newState = !isEmpty
        if (lastEmptinessState != newState) {
            controller?.isEnabled = newState
            if (lastEmptinessState && !newState) payThePrice()
            lastEmptinessState = newState
        }
    }

    companion object {
        const val HANDLE_INSERTED = 0
        const val HANDLE_DELETED = 1
        const val HANDLE_CHANGED = 2
        const val HANDLE_RESET = 3
    }
}
