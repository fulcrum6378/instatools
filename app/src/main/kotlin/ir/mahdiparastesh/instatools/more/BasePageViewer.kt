package ir.mahdiparastesh.instatools.more

import android.annotation.SuppressLint
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.selection.SelectionTracker
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.list.ListPost
import ir.mahdiparastesh.instatools.more.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.Selective
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.shake
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
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

    @SuppressLint("UnsafeOptInUsageError")
    inner class SelectObserver : SelectionTracker.SelectionObserver<String>() {
        override fun onItemStateChanged(key: String, selected: Boolean) {
            if (c.tbTitle == null) return
            BadgeUtils.detachBadgeDrawable(c.selectionBadge, c.tbTitle!!)
            if (c.tbTitle?.parent == null) return
            BadgeUtils.attachBadgeDrawable(
                BadgeDrawable.create(ContextThemeWrapper(c, UiTools.materialTheme)).apply {
                    number = tracker?.selection?.size() ?: 0
                    backgroundColor = c.themeColor(android.R.attr.colorAccent)
                    badgeTextColor =
                        if (c.night()) c.themeColor(android.R.attr.colorPrimary)
                        else c.color(R.color.defBG)
                    c.selectionBadge = this
                    maxCharacterCount = UiTools.MAX_BADGE_CHAR
                }, c.tbTitle!!
            )
        }

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
            else {
                BadgeUtils.detachBadgeDrawable(c.selectionBadge, c.tbTitle!!)
                c.selectionBadge = null
            }
        }
    }
}
