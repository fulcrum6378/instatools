package ir.mahdiparastesh.instatools.more

import android.os.Handler
import android.view.LayoutInflater
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.databinding.GuestModeBinding
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis

abstract class BasePage(val c: Main) : Fragment(), BackStackOwner, Toolbar.OnMenuItemClickListener {
    abstract var inflater: LayoutInflater
    abstract var handler: Handler?

    abstract fun updateShadow()
    abstract fun updateJumper()
    abstract fun onLoaded()
    abstract fun onFailed(message: String)

    protected fun guestMode(parent: ConstraintLayout, theme: BaseActivity.Theme) {
        val gb = GuestModeBinding.inflate(c.themeInflater(theme, c.layoutInflater), parent, true)
        gb.root.typeface = c.fontRegular
        onLoaded()
        for (ch in parent) if (ch is RecyclerView) ch.vis(false)
    }

    companion object {
        const val HANDLE_FETCHED = 0
        const val HANDLE_ABORTED = 1
    }
}
