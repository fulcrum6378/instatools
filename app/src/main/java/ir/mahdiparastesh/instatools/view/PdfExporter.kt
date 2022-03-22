package ir.mahdiparastesh.instatools.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Size
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.get
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.list.ListThd.Companion.onBind
import ir.mahdiparastesh.instatools.list.ListThd.Companion.onCreate
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BaseExporter
import ir.mahdiparastesh.instatools.more.Persistent
import java.io.FileOutputStream

abstract class PdfExporter(c: Persistent, exp: Exportable) : BaseExporter(c, exp) {

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
            c.c.contentResolver.openFileDescriptor(Uri.parse(Api.encode(exp.uri)), "w")?.use {
                FileOutputStream(it.fileDescriptor).use { fos -> document.writeTo(fos) }
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
            } while ((measuredHeight - cutAt) < size.height && iMess < exp.threadData!!.items.size)
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
        ).onCreate(
            Typeface.createFromAsset(c.assets, c.getString(R.string.font_regular)),
            Typeface.createFromAsset(c.assets, c.getString(R.string.font_light)),
            true
        ).onBind(c, exp.threadData!!.items, i, downloaded = exp.media).root

    private fun percent(mess: Int) {
        progress(
            if (mess == 0) 0f else ((100f / exp.threadData!!.items.size.toFloat()) * mess.toFloat()),
            false
        )
    }

    companion object {
        val size = Size(1190, 1680)
    }
}
