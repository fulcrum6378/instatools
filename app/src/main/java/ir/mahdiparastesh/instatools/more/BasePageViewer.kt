package ir.mahdiparastesh.instatools.more

import android.annotation.SuppressLint
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

@SuppressLint("NotifyDataSetChanged")
abstract class BasePageViewer(c: Viewer) : BasePage<Viewer>(c) {
    var tracker: SelectionTracker<String>? = null
    var selectivity = false

    override val selectiveMenuRes = R.menu.viewer_tlb_select

    abstract fun bInitialised(): Boolean

    override fun updateShadow() {
        c.b.tbShadow.vish(rv().computeVerticalScrollOffset() > 0)
    }

    fun reset() {
        if (bInitialised()) rv().adapter?.notifyDataSetChanged()
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
            UiTools.shake(c.c)
        }
    }

    inner class Saver(selection: Selection<String>) : BaseSaver(selection) {
        override fun handle() {
            val svd = list.getOrNull(0)
            if (svd == null) {
                Viewer.handler?.obtainMessage(PageSvd.HANDLE_INIT_QUEUER)?.sendToTarget()
                interrupt()
                return
            }
            c.m.vwUser?.edges()?.find { it.node.id == svd }?.let { edge ->
                c.dao.addQueued(
                    Queued(Persistent.now(), Api.Type.POST_ITEM.url.format(edge.node.shortcode))
                )
            }
            ended()
        }
    }
}
