package ir.mahdiparastesh.instatools.more

import ir.mahdiparastesh.instatools.data.Exportable

abstract class BaseExporter(protected val c: Persistent, protected val exp: Exportable) : Thread() {
    abstract fun progress(percent: Float, succeeded: Boolean)
}
