package ir.mahdiparastesh.instatools.exp

import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.job.ExportTask
import java.io.File
import java.io.FileOutputStream

abstract class BaseExporter(protected val exp: Exportable) {
    abstract val method: ExportTask.Method

    fun write(data: ByteArray, page: Int) {
        FileOutputStream(File(exp.name, "${page + 1}.${method.ext}")).use { it.write(data) }
    }
}