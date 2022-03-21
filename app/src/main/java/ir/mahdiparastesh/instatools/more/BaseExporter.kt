package ir.mahdiparastesh.instatools.more

import android.content.Context
import android.net.Uri
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.serv.Exporter

abstract class BaseExporter(
    protected val c: Context,
    protected val list: List<Dm>,
    protected val media: HashMap<String, Exporter.Downloadable>,
    protected val uri: Uri
) : Thread() {
    abstract fun progress(percent: Float, succeeded: Boolean)
}
