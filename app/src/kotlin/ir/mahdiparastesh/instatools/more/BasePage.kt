package ir.mahdiparastesh.instatools.more

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import ir.mahdiparastesh.instatools.view.OnlineDataLoader

/** Abstract class for all page fragments which reside inside a BaseActivity. */
abstract class BasePage<C> : Fragment(), Toolbar.OnMenuItemClickListener, OnlineDataLoader
    where C : BaseActivity {
    var ftDetached = false

    @Suppress("UNCHECKED_CAST")
    protected val c: C by lazy { activity as C }
    // If you use "get()", it'll throw NullPointerException in picture-in-picture!

    abstract val selectiveMenuRes: Int?
    override val heightPixels: Int by lazy { c.dm.heightPixels }
    override var shouldShowJumper = MutableLiveData(false)
    override var anJumper: ObjectAnimator? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prepareListing(c)
    }

    override fun onResume() {
        super.onResume()
        ftDetached = false
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
