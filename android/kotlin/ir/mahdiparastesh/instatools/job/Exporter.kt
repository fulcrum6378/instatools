package ir.mahdiparastesh.instatools.job

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.api.Api
import ir.mahdiparastesh.instatools.api.Media
import ir.mahdiparastesh.instatools.api.Rest
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.exp.HtmlExporter
import ir.mahdiparastesh.instatools.exp.PdfExporter
import ir.mahdiparastesh.instatools.exp.TxtExporter
import ir.mahdiparastesh.instatools.util.ForegroundService
import ir.mahdiparastesh.instatools.util.Persistent
import ir.mahdiparastesh.instatools.view.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.set

class Exporter : ForegroundService() {
    private var job: Job? = null
    private val cacheTree: File by lazy { File(c.cacheDir, "exporter") }

    override val com: ForegroundServiceCompanion get() = Companion
    override lateinit var ntfTitle: String

    companion object : ForegroundServiceCompanion() {
        override val klass = Exporter::class.java
        override val channel = Notify.Channel.EXPORTER
        override val ntfId = Notify.ID_EXPORTER
        override val ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.stop
        )

        const val DIR_MIME = "vnd.android.document/directory"
        const val USER_PROFILE_IMG = "user_%s"
        val fileTypes = arrayOf(
            "image/jpg" to "jpg", "video/mp4" to "mp4", "audio/mp4" to "m4a", "image/gif" to "gif"
        ) // TODO add support for other extensions
        var ntfDoneIdInc = 0

        fun canCreateDirSelf(c: Persistent) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            c.sPreference(Settings.spStorage) != null
    }

    override fun onCreate() {
        super.onCreate()
        ntfTitle = getString(R.string.exporterTitle)
        initialNotification(Companion, Main::class, 2)

        if (job?.isActive != true)
            job = CoroutineScope(Dispatchers.IO).launch { export() }
    }

    private suspend fun export() {
        var q: Exportable? = null
        while (dao.firstExportable().also { q = it } != null)
            q!!.handle()
    }

    private suspend fun Exportable.handle() {
        opt = Exportable.Options.parse(options)

        // fetch messages
        while (threadData?.has_older != false) {
            withContext(Dispatchers.Main) {
                ntfText = c.getString(
                    R.string.exporterFetchTexts, threadData?.items?.size ?: 0, threadData?.title()
                )
                updateNotification()
                // The API contains no reference to number of items in a thread.
            }

            val inbox = Api.call<Rest.InboxThread>(
                Api.Endpoint.DIRECT.url
                    .format(thread, threadData?.items?.firstOrNull()?.item_id ?: "", 75),
                Rest.InboxThread::class, onError = { code -> error(code) }
            )
            if (inbox == null) return
            val dmThd = inbox.thread
            if (dmThd == null) {
                error(-3)
                return; }

            if (threadData == null || dmThd.items.isNotEmpty()) {
                if (threadData == null)
                    threadData = dmThd
                else // if (dmThd.items.isNotEmpty()) obviously true
                    threadData?.items?.addAll(dmThd.items)
                threadData!!.items.sortBy { it.timestamp }
            }

            delay(1000)
        }

        // index media
        media = hashMapOf()
        if (opt?.img() != false || opt?.vid() != false || opt?.voi() != false) {
            threadData?.thread_id?.also {
                cacheBranch = File(cacheTree, it)
                if (!cacheBranch!!.exists()) cacheBranch!!.mkdirs()
            }
            val img = opt?.img() == true
            val vid = opt?.vid() == true
            val actVid = opt?.actVid() == true
            if (img) for (user in threadData!!.users) {
                val key = USER_PROFILE_IMG.format(user.pk)
                media[key] = Downloadable(user.picture(), 0, cacheBranch!!, key, 0)
            }
            for (dm in threadData!!.items) {
                if (actVid && dm.animated_media != null) continue
                // HTML gets GIFs dynamically, PDF cannot show them properly, and TXT...
                if (dm.voice_media != null) {
                    if (opt?.voice == 0 && dm.voice_media!!.media != null)
                        media[dm.item_id] = Downloadable(
                            dm.voice_media!!.media!!.audio.audio_src,
                            2,
                            cacheBranch!!,
                            dm.item_id,
                            -2
                        )
                    continue; }
                if (opt?.img() == true || opt?.vid() == true) (when {
                    vid && dm.clip != null -> dm.clip!!.clip
                    dm.direct_media_share != null -> when (dm.direct_media_share!!.media.media_type) {
                        1f, 2f -> if (img || vid) dm.direct_media_share!!.media else null
                        8f -> dm.direct_media_share!!.media.carousel_media
                            ?.let { if (img || vid) it[0] else null }

                        else -> null
                    }
                    vid && dm.felix_share != null -> dm.felix_share!!.video
                    dm.media != null -> if (img || vid) dm.media else null
                    dm.media_share != null -> when (dm.media_share!!.media_type) {
                        1f, 2f -> if (img || vid) dm.media_share else null
                        8f -> dm.media_share!!.carousel_media
                            ?.let { if (img || vid) it[0] else null }
                        else -> null
                    }
                    img && dm.raven_media != null -> dm.raven_media
                    dm.reel_share != null -> if (img || vid) dm.reel_share!!.media else null
                    dm.story_share != null -> if (img || vid) dm.story_share!!.media else null
                    else -> null
                })?.apply {
                    val theVer = carousel_media?.getOrNull(0) ?: this
                    val quality = when {
                        theVer.video_versions != null && opt!!.video == 3 ->
                            if (img) -opt!!.image.toFloat() else Media.Version.MEDIUM
                        theVer.video_versions != null -> -opt!!.video.toFloat()
                        else -> -opt!!.image.toFloat()
                    }
                    theVer.nearest(quality, justImage = opt?.actVid() != true)?.also { url ->
                        media[dm.item_id] = Downloadable(
                            url, if (opt?.actVid() == true && video_versions != null) 1 else 0,
                            cacheBranch!!, dm.item_id, quality.toInt()
                        )
                    }
                }
            }
        }

        // download media
        val iAll = media.entries.size
        var queueSize: Int
        var downloaded: Boolean
        for (dl in media.entries.filter { !it.value.cache.exists() }.also { queueSize = it.size }) {
            // || it.value.cache.length() == 0L
            withContext(Dispatchers.Main) {
                threadData?.title()?.also { title ->
                    ntfText = c.getString(R.string.exporterFetchMedia, queueSize + 1, iAll, title)
                }
                updateNotification(queueSize to iAll)
            }

            // download the file
            downloaded = false
            var retry = -1
            while (!downloaded) {
                retry++
                if (retry > 5) {
                    //media[dl.key]!!.write(byteArrayOf())
                    continue // skip the damn file!
                }

                val con = URI(dl.value.url).toURL().openConnection() as HttpsURLConnection
                con.requestMethod = "GET"
                con.useCaches = false
                con.connectTimeout = Api.DEFAULT_CONNECT_TIMEOUT
                con.doInput = true
                con.readTimeout = when (dl.value.type) {
                    1 -> 2 * 60000
                    2 -> 60000
                    else -> 15000
                }
                try {
                    con.connect()
                } catch (_: SocketTimeoutException) {
                    continue
                }

                if (con.responseCode == 200) try {
                    FileOutputStream(media[dl.key]!!.cache).use { fos ->
                        con.inputStream.use { it.copyTo(fos) }
                    }
                    downloaded = true
                } catch (_: IOException) {
                }
            }

            queueSize--
        }

        withContext(Dispatchers.Main) {
            threadData?.title()?.also { ntfText = c.getString(R.string.exporterWriting, it) }
            updateNotification()
        }
        when (type) {
            0 -> object : HtmlExporter(this@Exporter, this@handle) {
                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(this@handle, succeeded, rescueFolder?.uri)
                }
            }.start()

            1 -> object : PdfExporter(this@Exporter, this@handle) {
                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(this@handle, succeeded)
                }
            }.start()

            2 -> object : TxtExporter(this@Exporter, this@handle) {
                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(this@handle, succeeded)
                }
            }.start()

            else -> end(this, false)
            // single-file exports do NOT need rescuing;
            // they send files even to the cloud and virtual folders!
        }
    }

    private fun error(code: Int) {
        eventNotification(Notify.ID_EXPORTER_ERROR) {
            setContentTitle(getString(R.string.exporterFailed))
            setContentText(getString(Api.error(code), code))
            addAction(0, getString(R.string.tryAgain), pi(c, ACTION_START))
        }
        finish(false)
    }

    private val endedOnes = arrayListOf<Long>()
    private fun end(oldExp: Exportable?, succeeded: Boolean, alternativeFolder: Uri? = null) {
        if (oldExp == null || oldExp.addedAt in endedOnes) return
        endedOnes.add(oldExp.addedAt)
        // Don't update notification here; it'll create another after onDestroy
        CoroutineScope(Dispatchers.IO).launch {
            dao.deleteExportable(oldExp)
            incrementCounter(Settings.spExportCount)
            withContext(Dispatchers.Main) {
                eventNotification(Notify.ID_EXPORTER_DONE + ntfDoneIdInc) {
                    setContentTitle(
                        getString(
                            if (succeeded) R.string.exporterDone else R.string.exporterAborted,
                            oldExp.threadData?.title() ?: ""
                        )
                    )
                    if (succeeded && alternativeFolder != null) setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(getString(R.string.exportRescued))
                    )
                    if (succeeded) addAction(
                        0, getString(R.string.openFolder), PendingIntent.getActivity(
                            c, 0, Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(alternativeFolder
                                    ?: (if (oldExp.method().asTree) oldExp.uri
                                        ?.let { Uri.parse(it) }
                                    else oldExp.uri
                                        ?.let { DocumentFile.fromSingleUri(c, Uri.parse(it)) }
                                        ?.parentFile?.uri), DIR_MIME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }, ntfMutability()
                        )
                    )
                }
                ntfDoneIdInc++
            }
        }
    }

    enum class Method(
        val id: Int, val mime: String, val ext: String, val asTree: Boolean, val img: Boolean,
        val vid: Boolean, @StringRes val desc: Int
    ) {
        HTML(
            0, DIR_MIME, "html", true, true, true,
            R.string.exportHtmlDesc
        ),
        PDF(
            1, "application/pdf", "pdf", false, true, false,
            R.string.exportPdfDesc
        ),
        TXT(
            2, "text/plain", "txt", false, false, false,
            R.string.exportTxtDesc
        ),
    }

    inner class Downloadable(
        val url: String, val type: Int, folder: File, dmId: String, quality: Int
    ) { // TYPE: 0=>IMG, 1=>VID, 2=>AUD, 3=>GIF
        val cache = File(folder, "${dmId}_$quality.${fileTypes[type].second}")

        fun fileName(dmId: String) = "${dmId}.${fileTypes[type].second}"
    }
}
