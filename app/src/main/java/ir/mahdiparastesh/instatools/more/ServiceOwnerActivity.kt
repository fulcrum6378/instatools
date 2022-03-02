package ir.mahdiparastesh.instatools.more

import android.view.MenuItem
import ir.mahdiparastesh.instatools.R

abstract class ServiceOwnerActivity : BaseActivity() {
    private var lastEmptinessState = true

    private val controller: MenuItem? get() = toolbar.menu.findItem(R.id.mftControl)

    fun updateControlButton(it: Boolean) {
        controller?.apply {
            setIcon(if (it) R.drawable.pause else R.drawable.play)
            setTitle(if (it) R.string.stop else R.string.start)
        }
    }

    fun updateIfEmpty(isEmpty: Boolean) {
        val newState = !isEmpty
        if (lastEmptinessState != newState) {
            controller?.isEnabled = newState
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
