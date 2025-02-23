package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.job.DownloadTask
import ir.mahdiparastesh.instatools.job.ExportTask

object Context {
    val downloadTask: DownloadTask by lazy { DownloadTask() }
    val exportTask: ExportTask by lazy { ExportTask() }
}
