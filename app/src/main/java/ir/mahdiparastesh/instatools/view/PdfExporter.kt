package ir.mahdiparastesh.instatools.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.get
import ir.mahdiparastesh.instatools.more.Persistent
import java.io.FileOutputStream

abstract class PdfExporter(val c: Persistent, val uri: Uri) : Thread() {
    abstract val list: List<*>

    @SuppressLint("InflateParams")
    override fun run() {
        val document = PdfDocument()

        var page = 0
        var mess = 0

        while (mess < list.size) document.startPage(
            PdfDocument.PageInfo.Builder(size.width, size.height, page).create()
        ).apply {
            canvas.scale(1f, 1f)
            mess = insert(canvas, mess)
            document.finishPage(this)
            percent(mess)
            page++
        }

        try {
            c.c.contentResolver.openFileDescriptor(uri, "w")?.use {
                FileOutputStream(it.fileDescriptor).use { fos ->
                    document.writeTo(fos)
                }
            }
            progress(100f, true)
        } catch (ignored: Exception) {
            progress(100f, false)
        }
        document.close()
    }

    private var cutAt = 0
    private fun insert(canvas: Canvas, mess: Int): Int {
        var iMess = mess
        LinearLayout(c.c).apply {
            orientation = LinearLayout.VERTICAL
            do {
                addView(createView(context, this, iMess).apply {
                    if (cutAt > 0) translationY = -cutAt.toFloat()
                })
                measure( // ESSENTIAL BOTH FOR draw() and measuredHeight
                    View.MeasureSpec.makeMeasureSpec(canvas.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(canvas.height, View.MeasureSpec.UNSPECIFIED)
                ) // AT_MOST
                if ((measuredHeight - cutAt) >= size.height) {
                    cutAt = this[childCount - 1].measuredHeight -
                            ((measuredHeight - cutAt) - size.height)
                    if (cutAt <= 200) {
                        removeViewAt(childCount - 1)
                        cutAt = 0
                    }
                    break
                }
                iMess++
            } while ((measuredHeight - cutAt) < size.height && iMess < list.size)
            layout(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
        return iMess
    }

    private fun percent(mess: Int) {
        progress(if (mess == 0) 0f else ((100f / list.size.toFloat()) * mess.toFloat()), false)
    }

    abstract fun progress(percent: Float, succeeded: Boolean)

    abstract fun createView(c: Context, parent: ViewGroup, i: Int): View

    companion object {
        val size = Size(1190, 1680)
    }
}
