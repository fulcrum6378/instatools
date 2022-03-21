package ir.mahdiparastesh.instatools.view

import android.content.Context
import android.net.Uri
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.more.BaseExporter
import ir.mahdiparastesh.instatools.serv.Exporter

abstract class TxtExporter(
    c: Context, list: List<Dm>, media: HashMap<String, Exporter.Downloadable>, uri: Uri
) : BaseExporter(c, list, media, uri) {

    override fun run() {
    }
}
