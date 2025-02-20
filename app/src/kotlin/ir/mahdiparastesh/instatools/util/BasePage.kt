package ir.mahdiparastesh.instatools.util

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.view.OnlineLister
import kotlinx.coroutines.Job

/** Abstract class for all page fragments which reside inside a [BaseActivity]. */
abstract class BasePage<Activity> : Fragment(), OnlineLister, Toolbar.OnMenuItemClickListener
    where Activity : BaseActivity {

    // if you use "get()", it'll throw NullPointerException in picture-in-picture!
    @Suppress("UNCHECKED_CAST")
    protected val c: Activity by lazy { activity as Activity }

    abstract val selectiveMenuRes: Int?

    override var job: Job? = null
    override var shouldShowJumper: Boolean = false
    override var anJumper: ObjectAnimator? = null

    override fun screenHeight(): Int = c.dm.heightPixels

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refresher?.setOnRefreshListener(this)
        super.onViewCreated(view, savedInstanceState)
        prepareListing(c)
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
