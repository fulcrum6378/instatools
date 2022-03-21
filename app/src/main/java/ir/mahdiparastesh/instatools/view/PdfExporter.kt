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
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.list.ListThd.Companion.onBind
import ir.mahdiparastesh.instatools.list.ListThd.Companion.onCreate
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BaseExporter
import ir.mahdiparastesh.instatools.serv.Exporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileOutputStream

abstract class PdfExporter(
    c: Context, list: List<Dm>, media: HashMap<String, Exporter.Downloadable>, uri: Uri
) : BaseExporter(c, list, media, uri) {

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
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                c.contentResolver.openFileDescriptor(uri, "w")?.use {
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
    }

    private var cutAt = 0
    private fun insert(canvas: Canvas, mess: Int): Int {
        var iMess = mess
        LinearLayout(c).apply {
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

    private fun createView(c: Context, parent: ViewGroup, i: Int): View =
        ListThdBinding.inflate(
            LayoutInflater.from(c).cloneInContext(
                ContextThemeWrapper(c, BaseActivity.Theme.TERTIARY_LIGHT.res)
            ), parent, false
        ).onCreate(
            Typeface.createFromAsset(c.assets, c.getString(R.string.font_regular)),
            Typeface.createFromAsset(c.assets, c.getString(R.string.font_light)),
            true
        ).onBind(c, list, i, downloaded = media).root

    private fun percent(mess: Int) {
        progress(if (mess == 0) 0f else ((100f / list.size.toFloat()) * mess.toFloat()), false)
    }

    companion object {
        val size = Size(1190, 1680)
    }
}
