package ir.mahdiparastesh.instatools.more

import android.annotation.SuppressLint
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.view.Selective
import ir.mahdiparastesh.instatools.view.UiTools.shake
import ir.mahdiparastesh.instatools.view.UiTools.vish

abstract class BasePageViewer : BasePage<Viewer>(), Selective {
    override var tracker: SelectionTracker<String>? = null
    override var selectivity = false

    override val selectiveMenuRes = R.menu.viewer_tlb_select

    open fun avoidRefresh(): Boolean =
        rv()?.canScrollVertically(-1) == true || tracker?.hasSelection() == true

    override fun updateShadow() {
        if (bInitialised) c.b.tbShadow.vish(
            rv()!!.computeVerticalScrollOffset() > 0 && !c.expandable.zoomed
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    fun reset() {
        if (bInitialised) rv()?.adapter?.notifyDataSetChanged()
    }

    override fun goBack(): Boolean {
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return true
        }
        return false
    }

    inner class SelectObserver : SelectionTracker.SelectionObserver<String>() {
        override fun onSelectionChanged() {
            super.onSelectionChanged()
            val status = tracker?.hasSelection() == true
            if (selectivity == status) return
            selectivity = status
            c.selective(status)
            c.b.toolbar.menu.clear()
            c.b.toolbar.inflateMenu(if (status) R.menu.viewer_tlb_select else R.menu.viewer_tlb)
            c.fixTbMenu()
            c.shake()
            if (this@BasePageViewer is PageVwr) rv()?.isNestedScrollingEnabled = status
            if (status) (rv()?.adapter as ListPost<*, *>?)?.firstLongClickSelect = true
        }
    }
}
