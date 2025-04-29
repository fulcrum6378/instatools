package ir.mahdiparastesh.instatools.base

import android.annotation.SuppressLint
import android.view.View
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.view.Expandable
import kotlinx.coroutines.Job

/** Subclass of [BasePage], from which all pages of [Viewer] extend */
abstract class BasePageViewer : BasePage<Viewer>(), OnlineLister {

    override val tbShadow: View? by lazy { c.b.tbShadow }
    override val expandable: Expandable? get() = c.expandable
    override var job: Job? = null
    override val selectiveMenuRes = R.menu.viewer_tlb_select

    @SuppressLint("NotifyDataSetChanged")
    open fun clear() {
        if (isBInitialised()) rv?.adapter?.notifyDataSetChanged()
    }
}
