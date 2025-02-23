package ir.mahdiparastesh.instatools.job

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.clearCacheIfNecessary
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.data.DownloadHistory
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

class DownloadService : ForegroundService(), Downloader {
    private var dest: String? = null
    private var job: Job? = null
    private val stem by lazy { DocumentFile.fromTreeUri(c, Uri.parse(dest))!! }
    private val aliases = HashMap<String, String>()
    private var des: ParcelFileDescriptor? = null

    override val com: ForegroundServiceCompanion get() = Companion
    override lateinit var ntfTitle: String
    override val queue: CopyOnWriteArrayList<Queued> get() = m.queue
    override var q: Int = 0

    companion object : ForegroundServiceCompanion() {
        override val klass = DownloadService::class.java
        override val channel = Notify.Channel.DOWNLOADER
        override val ntfId = Notify.ID_DOWNLOADER
        override val ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.stop
        )
    }

    override fun onCreate() {
        super.onCreate()
        dest = sPreference(Settings.spStorage)
        CoroutineScope(Dispatchers.IO).launch {
            Settings.loadAliases(this@DownloadService, true)
                .forEach { (k, v) -> aliases[k] = v }
            if (sp != null) Settings.loadAliases(this@DownloadService, false)
                .forEach { (k, v) -> aliases[k] = v }
        }
        if (m.acc == null || dest == null) {
            finish(false); return; }

        ntfManager.cancel(Notify.ID_DOWNLOADER_ERROR)
        ntfTitle = getString(R.string.downloaderTitle)
        initialNotification(Companion, Downloads::class)
        if (job?.isActive != true)
            job = CoroutineScope(Dispatchers.IO).launch { start() }.also {
                it.invokeOnCompletion { onFinished() }
            }
    }

    override fun prepareOutput(q: Queued): FileOutputStream? {
        val branch: DocumentFile = when {
            q.owner in aliases && DocumentFile.fromTreeUri(c, Uri.parse(aliases[q.owner]))
                ?.exists() == true ->
                DocumentFile.fromTreeUri(c, Uri.parse(aliases[q.owner]))
            !q.isMainFile() -> stem
            @Suppress("KotlinConstantConditions")
            bPreference(
                Settings.spBranching, Settings.defSpBranching,
                Settings.spBranchingCb, Settings.defSpBranchingCb
            ) -> stem.findFile(q.owner) ?: stem.createDirectory(q.owner)
            else -> stem
        }!!
        var leaf = branch.findFile(q.fileName)
        if (leaf != null) { // file already exists
            if (leaf.length() > 0) return null
            // rewrite the file if it is corrupt
        } else
            leaf = branch.createFile(
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(q.ext)!!, q.fileName
            )!!
        des = c.contentResolver.openFileDescriptor(leaf.uri, "w")!!
        return FileOutputStream(des!!.fileDescriptor)
    }

    override fun handle(q: Queued): Boolean {
        // update the notification
        val queueSize = remaining()
        ntfTitle = getString(
            if (queueSize > 1) R.string.downloaderTitleCount else R.string.downloaderTitleCount1,
            queueSize
        )
        ntfSmallText = q.owner
        updateNotification()

        return super.handle(q)
    }

    override fun onRetry(q: Queued) {
    }

    override fun onSuccess(q: Queued) {
        des?.close()
        Downloads.handler?.obtainMessage(Downloads.HANDLE_DELETED, q)?.sendToTarget()
        if (q.isMainFile()) m.files?.add(q.fileName)
        incrementCounter(Settings.spDownloadCount)
    }

    override fun onFailure(q: Queued) {
        des?.close()
        q.status = 0b1
        Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, q)?.sendToTarget()
        incrementCounter(Settings.spDlErrorCount)
    }

    override fun onFinished() {
        finish(false)
    }

    override fun onFatalError(e: Exception) {
        if (e !is Api.FailureException) throw e

        eventNotification(Notify.ID_DOWNLOADER_ERROR) {
            setContentTitle(getString(R.string.download))
            setContentText(UiTools.apiError(c, e.code))
            setContentIntent(
                PendingIntent.getActivity(c, 0, Intent(c, Downloads::class.java), ntfMutability())
            )
        }
        finish(false)
    }

    override fun finish(cancelled: Boolean) {
        job?.cancel()
        ntfTitle = getString(R.string.downloaderTitle)
        ntfSmallText = null
        updateNotification()

        CoroutineScope(Dispatchers.IO).launch {
            val failedSum = queue.size
            if (failedSum != 0) eventNotification(Notify.ID_DOWNLOADER_SOME_FAILED) {
                setContentTitle(getString(R.string.downloaderSomeFailed, failedSum))
                setContentIntent(
                    PendingIntent.getActivity(
                        c, 0, Intent(c, Downloads::class.java), ntfMutability()
                    )
                )
            }

            clearCacheIfNecessary()
            @Suppress("KotlinConstantConditions")
            if (dest == null || !bPreference(
                    Settings.spAutoDeleteEmptyDirs, Settings.defSpAutoDeleteEmptyDirs,
                    Settings.spAutoDeleteEmptyDirsCb, Settings.defSpAutoDeleteEmptyDirsCb
                )
            ) return@launch
            val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
            for (branch in stem.listFiles())
                if (branch.isDirectory && branch.listFiles().isEmpty())
                    branch.delete()
            DownloadHistory.saveCache(this@DownloadService)
        }

        super.finish(cancelled)
    }
}
