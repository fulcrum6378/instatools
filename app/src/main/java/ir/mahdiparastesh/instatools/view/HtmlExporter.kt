package ir.mahdiparastesh.instatools.view

import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.more.BaseExporter
import ir.mahdiparastesh.instatools.more.Persistent

abstract class HtmlExporter(c: Persistent, exp: Exportable) : BaseExporter(c, exp) {

    override fun run() {
        progress(100f, true)
    }
}
