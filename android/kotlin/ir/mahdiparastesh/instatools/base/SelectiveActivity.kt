package ir.mahdiparastesh.instatools.base

import android.widget.TextView
import android.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.util.Delay
import ir.mahdiparastesh.instatools.view.UiTools.shake
import ir.mahdiparastesh.instatools.view.UiTools.vis

abstract class SelectiveActivity : BaseActivity() {

    private var isSelective = false

    /** Holds a [TextView] which enumerates selected items in [RecyclerView]. */
    val selectionCountView: TextView by lazy { findViewById<TextView>(R.id.selectionCount) }

    /**
     * Changes the "selective" mode;
     * in this mode the activity shows utilities for selection in a [RecyclerView].
     *
     * @param bb true if you just turned the selection on, false if you turned it off
     * @return false if the selective mode was already changed to "bb"
     */
    open fun selective(
        bb: Boolean,
        selectiveToolbarMenuRes: Int?,
        selectiveToolbarListener: Toolbar.OnMenuItemClickListener?
    ): Boolean {
        if (isSelective == bb) return false
        isSelective = bb

        // selectionCount
        selectionCountView.vis(bb)

        // Toolbar actions
        toolbar.menu.clear()
        toolbar.inflateMenu(if (bb) selectiveToolbarMenuRes!! else menuRes!!)
        toolbar.setOnMenuItemClickListener(if (isSelective) selectiveToolbarListener else this)
        Delay(100) { onPrepareOptionsMenu(toolbar.menu) }

        shake()
        return true
    }

    fun selectionCountChanged(n: Int) {
        selectionCountView.text = resources.getQuantityString(R.plurals.selectionCount, n, n)
    }
}
