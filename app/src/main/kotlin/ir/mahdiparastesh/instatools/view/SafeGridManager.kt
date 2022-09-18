@file:Suppress("unused")

package ir.mahdiparastesh.instatools.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

open class SafeGridManager : GridLayoutManager {
    constructor(
        context: Context, spanCount: Int, @RecyclerView.Orientation orientation: Int,
        reverseLayout: Boolean
    ) : super(context, spanCount, orientation, reverseLayout)

    constructor(
        context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, spanCount: Int) : super(context, spanCount)

    override fun onLayoutChildren(rv: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(rv, state)
        } catch (_: IndexOutOfBoundsException) {
        }
    }

    override fun onFocusSearchFailed(
        focused: View,
        focusDirection: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): View? {
        return try {
            super.onFocusSearchFailed(focused, focusDirection, recycler, state)
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }
}
