package ir.mahdiparastesh.instatools.expt

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Size
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.get
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.list.ListThd.Companion.onBind
import ir.mahdiparastesh.instatools.list.ListThd.Companion.onCreate
import ir.mahdiparastesh.instatools.util.BaseActivity
import ir.mahdiparastesh.instatools.job.Exporter
import java.io.FileOutputStream

abstract class PdfExporter(c: Exporter, exp: Exportable) : BaseExporter(c, exp) {
    private val sumOfAll: Int by lazy { exp.threadData!!.items.size }

    @SuppressLint("InflateParams")
    override fun run() {
        if (exp.threadData == null) {
            progress(100f, false); return; }
        val document = PdfDocument()
        var page = 0
        var mess = 0

        while (mess < exp.threadData!!.items.size) document.startPage(
            PdfDocument.PageInfo.Builder(size.width, size.height, page).create()
        ).apply {
            canvas.scale(1f, 1f)
            mess = insert(canvas, mess)
            document.finishPage(this)
            percent(mess)
            page++
        }
        runCatching {
            try {
                c.c.contentResolver.openFileDescriptor(Uri.parse(Api.encode(exp.uri)), "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos -> document.writeTo(fos) }
                }
            } catch (_: SecurityException) {
                // TODO RESCUE THE EXPORT!!
                progress(100f, false)
                document.close()
            }
        }.onSuccess {
            progress(100f, true)
            document.close()
        }.onFailure {
            progress(100f, false)
            document.close()
        }
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
            } while ((measuredHeight - cutAt) < size.height && iMess < sumOfAll)
            layout(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
        return iMess
    }

    private fun createView(c: Context, parent: ViewGroup, i: Int): View =
        ListThdBinding.inflate(
            LayoutInflater.from(c).cloneInContext(
                ContextThemeWrapper(c, BaseActivity.Theme.TERTIARY_LIGHT.res)
            ), parent, false
        ).onCreate(true).onBind(c, exp.threadData!!, i, downloaded = exp.media).root

    // "sumOfAll" is incremented by 1, in order for the percentage not to be 100 while writing the file.
    private fun percent(mess: Int) {
        progress(
            if (mess == 0) 0f else ((100f / (sumOfAll.toFloat() + 1f)) * mess.toFloat()),
            false
        )
    }

    companion object {
        val size = Size(1190, 1680)
    }
}
