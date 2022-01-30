package ir.mahdiparastesh.instatools.more

import android.os.Handler
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import ir.mahdiparastesh.instatools.Main

abstract class BasePage(val c: Main) : Fragment(), BackStackOwner, Toolbar.OnMenuItemClickListener {
    abstract var handler: Handler?
    var fetching = false

    abstract fun updateShadow()

    protected fun guestMode(parent: ConstraintLayout, theme: BaseActivity.Theme) {
        // TODO: APPEND GUEST MODE
    }

    companion object {
        const val HANDLE_FETCHED = 0
    }

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
}
