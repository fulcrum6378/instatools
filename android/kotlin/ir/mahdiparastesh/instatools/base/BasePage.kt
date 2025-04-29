package ir.mahdiparastesh.instatools.base

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toolbar
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.frag.PageRel
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.list.ListRel
import ir.mahdiparastesh.instatools.list.ListSvd
import ir.mahdiparastesh.instatools.list.ListTag

/** Abstract class for all page [Fragment]s which reside inside a [MultiPagedActivity] */
abstract class BasePage<Activity> : Fragment(), Lister, Toolbar.OnMenuItemClickListener
    where Activity : MultiPagedActivity {

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
                c.selective(status, selectiveMenuRes)

                if (status)  // TODO is this necessary?
                    when (this@BasePage) {
                        is PageSvd -> {
                            if (selectionGuide != null) {
                                b.root.removeView(selectionGuide)
                                c.c.gsp.edit { putBoolean(Settings.spLearntSelection, true) }
                                b.rv.suppressLayout(false)
                            }
                            (rv?.adapter as ListSvd?)
                        }
                        is PageVwr -> gridAdapter()
                        is PageRel -> (rv?.adapter as ListRel?)
                        is PageTag -> (rv?.adapter as ListTag?)
                        else -> throw IllegalStateException()
                    }?.firstLongClickSelect = true
            }
        }

    /**
     * Handle onBackPressed action for this page.
     * @return false if no action is to be taken.
     */
    open fun goBack(): Boolean {
        return false
    }
}
