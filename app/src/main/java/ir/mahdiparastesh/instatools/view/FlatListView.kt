package ir.mahdiparastesh.instatools.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView
import androidx.annotation.AttrRes

class FlatListView(
    c: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int
) : ListView(c, attrs, defStyleAttr) {
    constructor(c: Context, attrs: AttributeSet?) : this(c, attrs, 0)
    constructor(c: Context) : this(c, null, 0)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec, MeasureSpec.makeMeasureSpec(Int.MAX_VALUE shr 2, MeasureSpec.AT_MOST)
        )
        layoutParams = layoutParams.apply { height = measuredHeight }
    }
}
