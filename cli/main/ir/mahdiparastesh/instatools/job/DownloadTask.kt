package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.util.LazyFile
import java.io.*
import java.util.concurrent.CopyOnWriteArrayList

class DownloadTask : Downloader {
    override val queue: CopyOnWriteArrayList<Queued> = CopyOnWriteArrayList()
    override var q: Int = 0
    val outputDir = File("./Downloads/")

    fun download(
        med: Media, idealSize: Int, link: String? = null, owner: String? = null
    ) {
        for (q in med.queue(idealSize, link, owner)) queue.add(q)
        start()
    }

    override fun start() {
        if (outputDir.isDirectory == false) outputDir.mkdir()
        super.start()
    }

    override fun prepareOutput(q: Queued): LazyFile<FileOutputStream>? {
        val file = File(outputDir, q.fileName)
        if (file.exists() && file.length() != 0L) {
            println("File `${q.fileName}` already exists! Overwrite? (y / any)")
            if (readlnOrNull() !in arrayOf("y", "Y", "yes")) return null
        }
        return LazyFile { FileOutputStream(file) }
    }

    override fun onRetry(q: Queued) {
        println("Retrying for ${q.link}")
    }

    override fun onSuccess(q: Queued) {
        println("Downloaded ${q.fileName}")
    }

    override fun onFailure(q: Queued) {
        println("Failed downloading ${q.link}")
    }

    override fun onFinished() {
    }

    override fun onFatalError(e: Exception) {
        throw e
    }
}
