package ir.mahdiparastesh.instatools.more

import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.serv.Exporter

abstract class BaseExporter(protected val c: Exporter, protected val exp: Exportable) : Thread() {
    abstract fun progress(percent: Float, succeeded: Boolean)
}
