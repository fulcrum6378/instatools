package ir.mahdiparastesh.instatools.view

import android.view.MenuItem
import ir.mahdiparastesh.instatools.R

/**
 * Subclass of BaseActivity which controls a Service and switches start/stop buttons.
 * This activity does NOT use bound services.
 * @see [ir.mahdiparastesh.instatools.util.ForegroundService]
 */
abstract class ServiceOwnerActivity : CounterActivity() {
    abstract val controllerId: Int
    private var lastEmptinessState = defaultState

    private val controller: MenuItem? get() = toolbar.menu.findItem(controllerId)

    fun updateControlButton(it: Boolean) {
        controller?.apply {
            setIcon(if (it) R.drawable.pause else R.drawable.play)
            setTitle(if (it) R.string.stop else R.string.start)
        }
    }

    fun updateIfEmpty(isEmpty: Boolean) {
        val newState = !isEmpty
        if (lastEmptinessState != newState) {
            onStateChanged(newState)
            lastEmptinessState = newState
        }
    }

    open fun onStateChanged(hasContent: Boolean) {
        controller?.isEnabled = hasContent
    }

    companion object {
        const val HANDLE_INSERTED = 0
        const val HANDLE_DELETED = 1
        const val HANDLE_CHANGED = 2
        const val HANDLE_RESET = 3
        const val defaultState = true
    }
}
