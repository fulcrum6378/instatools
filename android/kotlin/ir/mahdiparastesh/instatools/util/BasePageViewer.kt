package ir.mahdiparastesh.instatools.util

import android.annotation.SuppressLint
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.selection.SelectionTracker
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Viewer
import ir.mahdiparastesh.instatools.frag.PageTag
import ir.mahdiparastesh.instatools.frag.PageVwr
import ir.mahdiparastesh.instatools.list.ListTag
import ir.mahdiparastesh.instatools.util.BaseActivity.Companion.night
import ir.mahdiparastesh.instatools.view.Expandable
import ir.mahdiparastesh.instatools.view.OnlineLister
import ir.mahdiparastesh.instatools.view.PostSelector
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.shake
import ir.mahdiparastesh.instatools.view.UiTools.themeColor
import kotlinx.coroutines.Job

/** Subclass of [BasePage], from which all pages of [Viewer] extend. */
abstract class BasePageViewer : BasePage<Viewer>(), OnlineLister {

    override val tbShadow: View? by lazy { c.b.tbShadow }
    override val expandable: Expandable? get() = c.expandable
    override var job: Job? = null
    override val selectiveMenuRes = R.menu.viewer_tlb_select

    @SuppressLint("NotifyDataSetChanged")
    open fun clear() {
        if (isBInitialised()) rv?.adapter?.notifyDataSetChanged()
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun createSelectionObserver() = if (this !is PostSelector) null
    else object : SelectionTracker.SelectionObserver<Long>() {

        override fun onItemStateChanged(key: Long, selected: Boolean) {
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
            if (status)
                when (this@BasePageViewer) {
                    is PageVwr -> gridAdapter()
                    is PageTag -> (rv?.adapter as ListTag?)
                    else -> throw IllegalStateException()
                }?.firstLongClickSelect = true
            else {
                BadgeUtils.detachBadgeDrawable(c.selectionBadge, c.tbTitle!!)
                c.selectionBadge = null
            }
        }
    }
}
