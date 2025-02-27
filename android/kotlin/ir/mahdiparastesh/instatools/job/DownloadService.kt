package ir.mahdiparastesh.instatools.job

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.annotation.MainThread
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.clearCacheIfNecessary
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.data.DownloadHistory
import ir.mahdiparastesh.instatools.data.Pickle
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.LazyFile
import ir.mahdiparastesh.instatools.util.Utils
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
    private val pickle: Pickle by lazy {
        Pickle(c.filesDir, m.acc!!.id, Pickle.Type.DOWNLOAD_LIST, null)
    }

    override val com: ForegroundServiceCompanion get() = Companion
    override lateinit var ntfTitle: String
    override val queue: CopyOnWriteArrayList<Download> get() = m.queue
    override var finishedItems: Int = 0

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
        if (m.acc == null || dest == null) return

        ntfManager.cancel(Notify.ID_DOWNLOADER_ERROR)
        ntfManager.cancel(Notify.ID_DOWNLOADER_SOME_FAILED)
        ntfTitle = getString(R.string.downloaderTitle)
        initialNotification(Companion, Downloads::class)

        job = CoroutineScope(Dispatchers.IO).launch {
            if (m.queue.isEmpty())
                pickle.restore<List<Download>>()
                    ?.also { m.queue.addAll(it) }
            start()
        }
    }

    override fun prepareOutput(q: Download): LazyFile<FileOutputStream>? {
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
        }
        return LazyFile {
            if (leaf == null) leaf = branch.createFile(
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(q.ext)!!, q.fileName
            )!!
            des = c.contentResolver.openFileDescriptor(leaf.uri, "w")!!
            FileOutputStream(des!!.fileDescriptor)
        }
    }

    override fun handle(q: Download, remaining: Int): Boolean {
        // update the notification
        ntfTitle = getString(
            if (remaining > 1) R.string.downloaderTitleCount else R.string.downloaderTitleCount1,
            remaining
        )
        ntfSmallText = q.owner
        updateNotification(Pair(finishedItems, remaining + finishedItems))

        return super.handle(q, remaining)
    }

    override fun onRetry(q: Download) {
    }

    override fun onSuccess(q: Download) {
        des?.close()
        m.findQueued(q)?.also {
            Downloads.handler?.obtainMessage(Downloads.HANDLE_DELETED, it)?.sendToTarget()
        }
        if (q.isMainFile()) m.files?.add(q.fileName)
        incrementCounter(Settings.spDownloadCount)
    }

    override fun onFailure(q: Download) {
        des?.close()
        m.findQueued(q)?.also {
            Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, it)?.sendToTarget()
        }
        incrementCounter(Settings.spDlErrorCount)
    }

    override fun onFinished() {
        CoroutineScope(Dispatchers.IO).launch {
            pickle.save(m.queue.toList())
        }
    }

    override fun onFatalError(e: Exception) {
        if (e !is Utils.InstaToolsException) throw e
        eventNotification(Notify.ID_DOWNLOADER_ERROR) {
            setContentTitle(getString(R.string.download))
            setContentText(
                when (e) {
                    is Api.FailureException -> UiTools.apiError(c, e.code)
                    is Downloader.FailureException -> getString(R.string.downloaderCannot)
                    is Queuer.FailureException -> getString(R.string.downloaderSomeFailed, e.times)
                    else -> throw IllegalStateException("IMPOSSIBLE?!")
                }
            )
            setContentIntent(
                PendingIntent.getActivity(c, 0, Intent(c, Downloads::class.java), ntfMutability())
            )
        }
    }

    @MainThread
    override fun onCancel() {
        val active = job?.isActive == true
        job?.cancel()
        onEnd(!active)
    }

    override fun onEnd(finished: Boolean) {
        ntfTitle = getString(R.string.downloaderTitle)
        ntfSmallText = null
        updateNotification()

        if (finished) {
            val failedSum = queue.size
            if (failedSum != 0) eventNotification(Notify.ID_DOWNLOADER_SOME_FAILED) {
                setContentTitle(getString(R.string.downloaderSomeFailed, failedSum))
                setContentIntent(
                    PendingIntent.getActivity(
                        c, 0, Intent(c, Downloads::class.java), ntfMutability()
                    )
                )
            }
        }

        clearCacheIfNecessary()
        @Suppress("KotlinConstantConditions")
        if (dest != null && bPreference(
                Settings.spAutoDeleteEmptyDirs, Settings.defSpAutoDeleteEmptyDirs,
                Settings.spAutoDeleteEmptyDirsCb, Settings.defSpAutoDeleteEmptyDirsCb
            )
        ) {
            val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
            for (branch in stem.listFiles())
                if (branch.isDirectory && branch.listFiles().isEmpty())
                    branch.delete()
        }
        DownloadHistory.saveCache(this@DownloadService)

        destroy()
    }
}
