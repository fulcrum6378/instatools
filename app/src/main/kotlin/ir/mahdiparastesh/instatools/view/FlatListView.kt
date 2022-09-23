package ir.mahdiparastesh.instatools.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ListView
import androidx.annotation.AttrRes

class FlatListView(
    context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int
) : ListView(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec, MeasureSpec.makeMeasureSpec(Int.MAX_VALUE shr 2, MeasureSpec.AT_MOST)
        )
        layoutParams = layoutParams.apply { height = measuredHeight }
    }
}
