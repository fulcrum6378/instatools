package ir.mahdiparastesh.instatools.job

import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.base.ForegroundService
import ir.mahdiparastesh.instatools.data.Download
import ir.mahdiparastesh.instatools.util.LazyFile
import ir.mahdiparastesh.instatools.util.StorageManager
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.UiTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileOutputStream

/**
 * A [ForegroundService] which queues [Download]s and performs them one after another
 */
class DownloadService : ForegroundService(), Downloader {
    private var dest: String? = null
    private val stem by lazy { DocumentFile.fromTreeUri(c, dest!!.toUri())!! }
    private val aliases = HashMap<String, String>()
    private var des: ParcelFileDescriptor? = null

    override val com: ForegroundServiceCompanion<Download> get() = Companion
    override val ntfChannel = Notify.Channel.DOWNLOADER
    override val ntfId = Notify.ID_DOWNLOADER
    override val ntfIcon: Int = R.drawable.downloader
    override val ntfFailureIcon: Int = R.drawable.notification
    override val ntfTint: Int = R.color.CSL
    override lateinit var ntfTitle: String
    override var ntfText: String? = null
    override var ntfSmallText: String? = null
    override val ntfActions: Array<Pair<Int, String>> =
        arrayOf(R.string.pause to ACTION_PAUSE, R.string.stop to ACTION_CANCEL)
    override var handledItems: Int = 0

    @Volatile
    override var proceed: Boolean = true

    companion object : ForegroundServiceCompanion<Download>()

    override fun onCreate() {
        super.onCreate()
        if (c.acc == null) return
        dest = c.sPreference(Settings.spStorage)
        if (dest == null) return

        ntfManager.cancel(Notify.ID_DOWNLOADER_PAUSED)
        ntfManager.cancel(Notify.ID_DOWNLOADER_ERROR)
        ntfManager.cancel(Notify.ID_DOWNLOADER_SOME_FAILED)
        ntfTitle = getString(R.string.downloaderTitle)
        initialNotification(Downloads::class)

        CoroutineScope(Dispatchers.IO).launch {

            // load the map of alias folders
            Settings.loadAliases(c, true)
                .forEach { (k, v) -> aliases[k] = v }
            if (c.sp != null) Settings.loadAliases(c, false)
                .forEach { (k, v) -> aliases[k] = v }

            // start looping
            start()
        }
    }

    override fun iterator(): Iterator<Download> = c.downloads.iterator<Download>()

    override fun prepareOutput(q: Download): LazyFile<FileOutputStream>? {
        val branch: DocumentFile = when {
            q.owner in aliases && DocumentFile.fromTreeUri(c, aliases[q.owner]!!.toUri())
                ?.exists() == true ->
                DocumentFile.fromTreeUri(c, aliases[q.owner]!!.toUri())
            !q.isMainFile() -> stem
            @Suppress("KotlinConstantConditions")
            c.bPreference(
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
        processingItem = q
        Downloads.handler?.obtainMessage(HANDLE_ITEM_UPDATED, c.downloads.indexOf<Download>(q))
            ?.sendToTarget()

        // update the notification
        ntfTitle = resources.getQuantityString(R.plurals.downloaderTitleCount, remaining, remaining)
        ntfSmallText = q.owner
        updateNotification(Pair(handledItems, remaining + handledItems))

        return super.handle(q, remaining)
    }

    override fun onRetry(q: Download) {
    }

    override fun onHandled(q: Download, success: Boolean) {
        super.onHandled(q, success)
        des?.close()
        processingItem = null
        Downloads.handler?.obtainMessage(
            if (success) HANDLE_ITEM_DELETED else HANDLE_ITEM_UPDATED,
            c.downloads.indexOf<Download>(q)
        )?.sendToTarget()
        if (success) {
            c.downloads.remove<Download>(q)
            if (q.isMainFile()) c.downloadHistory.add(q.fileName)
        }
        c.incrementCounter(if (success) Settings.spDownloadCount else Settings.spDlErrorCount)
    }

    override fun onPause() {
        onCancel()
        val remaining = c.downloads.size<Download>()
        if (remaining > 0) notifySuspension(Notify.ID_DOWNLOADER_PAUSED, null) {
            setContentTitle(getString(R.string.downloaderPaused))
            setContentText(
                resources.getQuantityString(R.plurals.taskPausedCount, remaining, remaining)
            )
        }
    }

    override fun onCancel() {
        proceed = false
    }

    override fun onFinished(fatalError: Exception?) {
        ntfTitle = getString(R.string.downloaderTitle)
        ntfSmallText = null
        updateNotification()

        // clear cached pictures of Glide
        c.storageManager.clearCacheIfNecessary(StorageManager.CacheSubdir.IMAGE)

        if (proceed) {
            if (fatalError != null) {
                if (fatalError !is Utils.InstaToolsException) throw fatalError

                // report the fatal error
                notifyFailure(Notify.ID_DOWNLOADER_ERROR, Downloader::class) {
                    setContentTitle(getString(R.string.download))
                    setContentText(
                        when (fatalError) {
                            is Api.FailureException ->
                                UiTools.apiError(c, fatalError.code)
                            is Downloader.FailureException, is Queuer.FailureException ->
                                getString(R.string.downloaderCannot)
                            else -> throw IllegalStateException("IMPOSSIBLE?!")
                        }
                    )
                }
            } else {
                // report if some downloads failed
                val failedSum = c.downloads.count<Download> { it.isFailed() }
                if (failedSum != 0) notifyFailure(
                    Notify.ID_DOWNLOADER_SOME_FAILED, Downloader::class,
                    failedSum < c.downloads.size<Download>()
                ) {
                    setContentTitle(
                        resources.getQuantityString(
                            R.plurals.downloaderSomeFailed, failedSum, failedSum
                        )
                    )
                }
            }
        }
        if (processingItem != null && (!proceed || fatalError != null)) {
            val index = c.downloads.indexOf<Download>(processingItem!!)
            processingItem = null
            Downloads.handler?.obtainMessage(HANDLE_ITEM_UPDATED, index)?.sendToTarget()
        }

        // remove empty directories
        @Suppress("KotlinConstantConditions")
        if (dest != null && c.bPreference(
                Settings.spAutoDeleteEmptyDirs, Settings.defSpAutoDeleteEmptyDirs,
                Settings.spAutoDeleteEmptyDirsCb, Settings.defSpAutoDeleteEmptyDirsCb
            )
        ) {
            val stem = DocumentFile.fromTreeUri(c, dest!!.toUri())!!
            for (branch in stem.listFiles())
                if (branch.isDirectory && branch.listFiles().isEmpty())
                    branch.delete()
        }

        // end the foreground service via the worker thread
        destroy()
    }
}
