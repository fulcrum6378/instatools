package ir.mahdiparastesh.instatools.exp

import ir.mahdiparastesh.instatools.job.Exporter
import java.io.File
import java.io.FileOutputStream

abstract class BaseExporter(protected val exp: Exporter.Exportable) {
    abstract val method: Exporter.Method

    fun write(data: ByteArray, page: Int) {
        FileOutputStream(File(exp.name, "${page + 1}.${method.ext}")).use { it.write(data) }
    }
}