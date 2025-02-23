package ir.mahdiparastesh.instatools

import ir.mahdiparastesh.instatools.job.Downloader
import ir.mahdiparastesh.instatools.job.Exporter

object Context {
    val downloader: Downloader by lazy { Downloader() }
    val exporter: Exporter by lazy { Exporter() }
}
