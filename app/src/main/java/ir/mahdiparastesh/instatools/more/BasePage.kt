package ir.mahdiparastesh.instatools.more

import android.os.Handler
import android.view.LayoutInflater
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.GuestModeBinding

abstract class BasePage(val c: Main) : Fragment(), BackStackOwner, Toolbar.OnMenuItemClickListener {
    abstract var inflater: LayoutInflater
    abstract var handler: Handler?
    var fetching = false

    abstract fun updateShadow()
    abstract fun updateJumper()
    abstract fun onLoad()

    protected fun guestMode(parent: ConstraintLayout, theme: BaseActivity.Theme) {
        val gb = GuestModeBinding.inflate(c.themeInflater(theme, c.layoutInflater), parent, true)
        gb.root.typeface = c.fontRegular
    }

    companion object {
        const val HANDLE_FETCHED = 0
        const val HANDLE_ABORTED = 1
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
