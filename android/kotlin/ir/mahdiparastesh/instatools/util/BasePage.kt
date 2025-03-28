package ir.mahdiparastesh.instatools.util

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toolbar
import androidx.fragment.app.Fragment
import ir.mahdiparastesh.instatools.view.Lister

/** Abstract class for all page fragments which reside inside a [BaseActivity]. */
abstract class BasePage<Activity> : Fragment(), Lister, Toolbar.OnMenuItemClickListener
    where Activity : BaseActivity {

    // if you use "get()", it'll throw NullPointerException in picture-in-picture!
    @Suppress("UNCHECKED_CAST")
    protected val c: Activity by lazy { activity as Activity }

    abstract val selectiveMenuRes: Int?

    override var shouldShowJumper: Boolean = false
    override var anJumper: ObjectAnimator? = null

    override fun screenHeight(): Int = c.dm.heightPixels

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prepareListing(c)
        updateShadow()
        updateJumper()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean = true

    /**
     * Handle onBackPressed action for this page.
     * @return false if no action is to be taken.
     */
    open fun goBack(): Boolean {
        return false
    }
}
