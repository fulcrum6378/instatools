@file:SuppressLint("AppCompatCustomView")

package ir.mahdiparastesh.instatools.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.annotation.AttrRes

class SoftEditText(
    c: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int
) : EditText(c, attrs, defStyleAttr) {
    constructor(c: Context, attrs: AttributeSet?) : this(c, attrs, android.R.attr.editTextStyle)
    constructor(c: Context) : this(c, null, android.R.attr.editTextStyle)

    override fun isTextSelectable(): Boolean = false
}

class SoftAutoCompleteTextView(
    c: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int
) : AutoCompleteTextView(c, attrs, defStyleAttr) {
    constructor(c: Context, attrs: AttributeSet?) :
        this(c, attrs, android.R.attr.autoCompleteTextViewStyle)

    constructor(c: Context) : this(c, null, android.R.attr.autoCompleteTextViewStyle)

    override fun isTextSelectable(): Boolean = false
}
