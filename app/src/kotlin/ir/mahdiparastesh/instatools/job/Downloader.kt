package ir.mahdiparastesh.instatools.job

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.clearCacheIfNecessary
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.data.DownloadHistory
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Utils
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.ServiceOwnerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URI
import javax.net.ssl.HttpsURLConnection

class Downloader : ForegroundService() {
    private var dest: String? = null
    private var job: Job? = null
    private val stem by lazy { DocumentFile.fromTreeUri(c, Uri.parse(dest))!! }
    private val aliases = HashMap<String, String>()

    override val com: ForegroundServiceCompanion get() = Companion
    override lateinit var ntfTitle: String

    companion object : ForegroundServiceCompanion() {
        override val klass = Downloader::class.java
        override val channel = Notify.Channel.QUEUER
        override val ntfId = Notify.ID_DOWNLOADER
        override val ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.stop
        )
    }

    override fun onCreate() {
        super.onCreate()
        dest = sPreference(Settings.spStorage)
        CoroutineScope(Dispatchers.IO).launch {
            Settings.loadAliases(this@Downloader, true)
                .forEach { (k, v) -> aliases[k] = v }
            if (sp != null) Settings.loadAliases(this@Downloader, false)
                .forEach { (k, v) -> aliases[k] = v }
        }
        if (m.acc == null || dest == null) {
            finish(false); return; }

        ntfManager.cancel(Notify.ID_DOWNLOADER_ERROR)
        ntfTitle = getString(R.string.queuerTitle)
        initialNotification(Companion, Downloads::class)
        if (job?.isActive != true)
            job = CoroutineScope(Dispatchers.IO).launch { download() }
    }

    suspend fun download() {
        var q: Queued? = null
        var queueSize: Int
        var binary: InputStream?
        while (dao.firstQueued().also { q = it } != null) {
            q!!

            // update the notification
            queueSize = dao.countReadyQueueds()
            ntfTitle = getString(
                if (queueSize > 1) R.string.queuerTitleCount else R.string.queuerTitleCount1,
                queueSize
            )
            ntfSmallText = q.userName
            updateNotification()

            // prepare a path
            val branch: DocumentFile = when {
                q.userName in aliases && DocumentFile.fromTreeUri(c, Uri.parse(aliases[q.userName]))
                    ?.exists() == true ->
                    DocumentFile.fromTreeUri(c, Uri.parse(aliases[q.userName]))
                !q.isMainFile() -> stem
                @Suppress("KotlinConstantConditions")
                bPreference(
                    Settings.spBranching, Settings.spBranchingCb, Settings.defSpBranching
                ) -> stem.findFile(q.userName) ?: stem.createDirectory(q.userName)
                else -> stem
            } ?: return
            val ext = q.extension()
            val fName = q.fName(ext)
            var leaf = branch.findFile(fName)
            // Never check existence from StorageCache because the file might be deleted anytime
            // and the user might want to re-download it!
            if (leaf != null) return
            leaf = branch.createFile(
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)!!, fName
            ) ?: return
            // Nevertheless files are RARELY duplicated with a " (1)" suffix.
            // It presumably happens during slow connections.
            // It could be because of simultaneous writing.
            val des = c.contentResolver.openFileDescriptor(leaf.uri, "w") ?: return

            // download the file
            binary = null
            var retry = -1
            while (binary == null) {
                retry++
                if (retry > 5) {
                    q.status = 0b1
                    Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_CHANGED, q)
                        ?.sendToTarget()
                    dao.updateQueued(q)
                    incrementCounter(Settings.spDlErrorCount)
                }

                val con = URI(q.url).toURL().openConnection() as HttpsURLConnection
                con.requestMethod = "GET"
                con.useCaches = false
                con.connectTimeout = Api.DEFAULT_CONNECT_TIMEOUT
                con.doInput = true
                con.readTimeout = when (q.type) {
                    Media.Type.IMAGE.num -> 15000
                    else -> q.dur?.let { (it * 2000f).toInt() } ?: (2 * 60000)
                }
                try {
                    con.connect()
                } catch (_: SocketTimeoutException) {
                    error(con.responseCode)
                    return; }

                if (con.responseCode == 200) try {
                    binary = con.inputStream
                } catch (_: IOException) {
                    error(con.responseCode)
                    return; }
            }

            // save the file
            val fos = FileOutputStream(des.fileDescriptor)
            when (ext) {
                "jpg" -> {
                    val ba = binary.readBytes()
                    val outputSet = (Imaging.getMetadata(ba) as JpegImageMetadata?)?.exif?.outputSet
                        ?: TiffOutputSet()
                    outputSet.orCreateRootDirectory.apply {
                        removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION) // Title + Subject
                        add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, q.link)
                        removeField(ExifTagConstants.EXIF_TAG_SOFTWARE)
                        add(ExifTagConstants.EXIF_TAG_SOFTWARE, Utils.INSTATOOLS)
                        removeField(TiffTagConstants.TIFF_TAG_ARTIST) // Authors
                        add(TiffTagConstants.TIFF_TAG_ARTIST, q.userName)
                        removeField(TiffTagConstants.TIFF_TAG_COPYRIGHT)
                        add(TiffTagConstants.TIFF_TAG_COPYRIGHT, "IG: @${q.userName}")
                    }
                    outputSet.orCreateExifDirectory.apply {
                        /*removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                        add( // Date taken
                            ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL,
                            tiffDate.format(q.addedAt)
                        )*/
                        q.caption?.also {
                            removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT)
                            add(ExifTagConstants.EXIF_TAG_USER_COMMENT, it)
                        }
                        removeField(ExifTagConstants.EXIF_TAG_SITE)
                        add(ExifTagConstants.EXIF_TAG_SITE, q.link)
                    }
                    ExifRewriter().updateExifMetadataLossless(ba, fos, outputSet)
                } // TODO location data?

                else -> binary.copyTo(fos) // TODO metadata for videos, PNG, WEBP, etc?
            }
            fos.close()
            des.close()
            if (q.isMainFile()) m.files?.add(fName)
            incrementCounter(Settings.spDownloadCount)

            Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_DELETED, q)?.sendToTarget()
            dao.deleteQueued(q)
        }

        finish(false)
    }

    private fun error(code: Int) {
        eventNotification(Notify.ID_DOWNLOADER_ERROR) {
            setContentTitle(getString(R.string.downloads))
            setStyle(NotificationCompat.BigTextStyle().bigText(getString(Api.error(code), code)))
            setContentIntent(
                PendingIntent.getActivity(c, 0, Intent(c, Downloads::class.java), ntfMutability())
            )
        }
        finish(false)
    }

    override fun destroy() {
        job?.cancel()
        ntfTitle = getString(R.string.queuerTitle)
        ntfSmallText = null
        updateNotification()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                clearCacheIfNecessary()
                @Suppress("KotlinConstantConditions")
                if (dest == null || !bPreference(
                        Settings.spAutoDeleteEmptyDirs, Settings.spAutoDeleteEmptyDirsCb,
                        Settings.defSpAutoDeleteEmptyDirs
                    )
                ) return@runCatching
                val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
                for (branch in stem.listFiles())
                    if (branch.isDirectory && branch.listFiles().isEmpty())
                        branch.delete()
            }.onFailure {
                if (BuildConfig.DEBUG) throw it
            }
            DownloadHistory.saveCache(this@Downloader)
            download() // double check in between
            super.destroy()
        }
    }
}
