package ir.mahdiparastesh.instatools.view

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.widget.TextView

class SafeLinkMovementMethod : LinkMovementMethod() {
    override fun onTouchEvent(widget: TextView?, buffer: Spannable?, event: MotionEvent?): Boolean {
        val action = event?.action

        if (widget != null && buffer != null &&
            (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN)
        ) {
            var x = event.x.toInt()
            var y = event.y.toInt()
            x -= widget.totalPaddingLeft
            y -= widget.totalPaddingTop
            x += widget.scrollX
            y += widget.scrollY
            val layout = widget.layout
            val line = layout.getLineForVertical(y)
            val off = layout.getOffsetForHorizontal(line, x.toFloat())
            val links = buffer.getSpans(off, off, ClickableSpan::class.java)

            if (links.isNotEmpty()) {
                val link = links[0]
                if (action == MotionEvent.ACTION_UP) {
                    if (link is URLSpan) try {
                        widget.context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                .putExtra(Browser.EXTRA_APPLICATION_ID, widget.context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: ActivityNotFoundException) {
                    } else link.onClick(widget)
                } else if (action == MotionEvent.ACTION_DOWN) Selection.setSelection(
                    buffer, buffer.getSpanStart(link), buffer.getSpanEnd(link)
                )
                return true
            } else Selection.removeSelection(buffer)
        }

        return super.onTouchEvent(widget, buffer, event)
    }

    companion object {
        @JvmStatic
        private var sInstance: SafeLinkMovementMethod? = null

        fun getInstance(): MovementMethod {
            if (sInstance == null) sInstance = SafeLinkMovementMethod()
            return sInstance!!
        }
    }
}
