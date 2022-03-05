package ir.mahdiparastesh.instatools.more

import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionTracker
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.view.UiTools.Companion.vish

abstract class BasePageViewer(c: Viewer) : BasePage<Viewer>(c) {
    var tracker: SelectionTracker<String>? = null

    abstract fun bInitialised(): Boolean

    override fun updateShadow() {
        c.b.tbShadow.vish(rv().computeVerticalScrollOffset() > 0)
    }

    fun reset() {
        if (bInitialised()) rv().adapter = null
        tracker = null
    }

    override fun goBack(): Boolean {
        if (tracker?.hasSelection() == true) {
            tracker?.clearSelection()
            return true
        }
        return false
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
