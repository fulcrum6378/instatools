package ir.mahdiparastesh.instatools.job

import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.util.LazyFile
import ir.mahdiparastesh.instatools.util.Queue
import java.io.*

class DownloadTask : Downloader {
    val outputDir = File("./Downloads/")
    val queue: Queue<Download> = Queue(null)
    override var handledItems: Int = 0
    override var proceed: Boolean = true

    fun download(
        med: Media, idealSize: Int, link: String? = null, owner: String? = null
    ) {
        queue.addAll<Download>(med.queue(idealSize, link, owner))
        start()
    }

    override fun start() {
        if (outputDir.isDirectory == false) outputDir.mkdir()
        super.start()
    }

    override fun iterator(): Iterator<Download> = queue.iterator<Download>()

    override fun prepareOutput(q: Download): LazyFile<FileOutputStream>? {
        val file = File(outputDir, q.fileName)
        if (file.exists() && file.length() != 0L) {
            println("File `${q.fileName}` already exists! Overwrite? (y / any)")
            if (readlnOrNull() !in arrayOf("y", "Y", "yes")) return null
        }
        return LazyFile { FileOutputStream(file) }
    }

    override fun onRetry(q: Download) {
        println("Retrying for ${q.link}")
    }

    override fun onHandled(q: Download, success: Boolean) {
        super.onHandled(q, success)
        if (success) queue.remove<Download>(q)
        println("${if (success) "Downloaded" else "Failed downloading"} ${q.fileName}")
    }

    override fun onFinished(fatalError: Exception?) {
        if (fatalError != null) throw fatalError
    }
}
