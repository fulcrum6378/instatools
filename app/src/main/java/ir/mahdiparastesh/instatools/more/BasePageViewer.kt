package ir.mahdiparastesh.instatools.more

import android.annotation.SuppressLint
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.view.UiTools.Companion.shake
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vis
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

@SuppressLint("NotifyDataSetChanged")
abstract class BasePageViewer(c: Viewer) : BasePage<Viewer>(c) {
    var tracker: SelectionTracker<String>? = null
    var selectivity = false

    override val selectiveMenuRes = R.menu.viewer_tlb_select

    override fun updateShadow() {
        if (bInitialised) c.b.tbShadow.vish(rv().computeVerticalScrollOffset() > 0)
    }

    fun reset() {
        if (bInitialised) rv().adapter?.notifyDataSetChanged()
    }

    override fun goBack(): Boolean {
        if (c.expandable.zoomed) {
            jumper().vis(true)
            c.expandable.collapse(); return true; }
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
        }
    }
}
