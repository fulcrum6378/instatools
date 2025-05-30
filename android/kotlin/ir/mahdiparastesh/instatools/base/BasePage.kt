package ir.mahdiparastesh.instatools.base

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.SelectionTracker

/** Abstract class for all page [Fragment]s which reside inside a [SelectiveActivity] */
abstract class BasePage<Activity> : Fragment(), Lister, Toolbar.OnMenuItemClickListener
    where Activity : SelectiveActivity {

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

    fun createSelectionObserver(): SelectionTracker.SelectionObserver<Long>? =
        if (this !is PostSelector) null
        else object : SelectionTracker.SelectionObserver<Long>() {

            override fun onItemStateChanged(key: Long, selected: Boolean) {
                c.selectionCountChanged(tracker?.selection?.size() ?: 0)
            }

            override fun onSelectionChanged() {
                super.onSelectionChanged()
                val status = tracker?.hasSelection() == true
                if (selectivity == status) return
                selectivity = status
                c.selective(status, selectiveMenuRes, this@BasePage)
                if (status) onSelectionStarted()
            }
        }

    open fun onSelectionStarted() {
    }

    /**
     * Handle onBackPressed action for this page.
     * @return false if no action is to be taken.
     */
    open fun goBack(): Boolean {
        return false
    }
}
