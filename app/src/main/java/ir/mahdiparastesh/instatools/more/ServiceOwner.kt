package ir.mahdiparastesh.instatools.more

import android.view.MenuItem
import ir.mahdiparastesh.instatools.R

interface ServiceOwner {
    fun findControl(): MenuItem?

    fun updateControlButton(it: Boolean) {
        findControl()?.apply {
            setIcon(if (it) R.drawable.pause else R.drawable.play)
            setTitle(if (it) R.string.stop else R.string.start)
        }
    }

    companion object {
        const val HANDLE_INSERTED = 0
        const val HANDLE_DELETED = 1
        const val HANDLE_CHANGED = 2
        const val HANDLE_RESET = 3
    }
}
