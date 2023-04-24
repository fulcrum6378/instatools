package ir.mahdiparastesh.instatools.serv

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.expt.HtmlExporter
import ir.mahdiparastesh.instatools.expt.PdfExporter
import ir.mahdiparastesh.instatools.expt.TxtExporter
import ir.mahdiparastesh.instatools.frag.PageBox.FetchOfThread
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.json.Versioned
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.view.Notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class Exporter : ForegroundService() {
    private var exp: Exportable? = null
    private val cache: File by lazy { Cache(c) }
    private val reqQueue by lazy { Volley.newRequestQueue(c) }

    override val com: ForegroundServiceCompanion get() = Companion
    override lateinit var ntfTitle: String
    override val requiresHandling = false

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
        )
        var ntfDoneIdInc = 0

        fun canCreateDirSelf(c: Persistent) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            c.sPreference(Settings.spStorage) != null
    }

    override fun onCreate() {
        super.onCreate()
        ntfTitle = getString(R.string.exporterTitle)
        initialNotification(Companion, Main::class, 2)
        if (!cache.exists()) cache.mkdir()
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    BasePage.HANDLE_FETCHED -> {
                        val dmThd = msg.obj as Dm.DmThread
                        if (exp?.threadData == null || dmThd.items.isNotEmpty()) {
                            if (exp?.threadData == null)
                                exp?.threadData = dmThd
                            else // if (dmThd.items.isNotEmpty()) obviously true
                                exp?.threadData?.items?.addAll(dmThd.items)
                            ntfText = c.getString(
                                R.string.exporterFetchTexts,
                                exp?.threadData?.items?.size ?: 0,
                                dmThd.title()
                            )
                            updateNotification()
                            // Inbox API has no reference to number of items in a thread.
                            exp?.fetchData()
                        } else CoroutineScope(Dispatchers.IO).launch {
                            exp?.fetchMedia()
                        }
                    }
                    Api.HANDLE_ERROR -> {
                        when (val code = (msg.obj as NetworkResponse?)?.statusCode) {
                            429 -> eventNotification(Notify.ID_EXPORTER_429) {
                                setContentTitle(getString(R.string.exporterFailed))
                                setStyle(
                                    NotificationCompat.BigTextStyle()
                                        .bigText(getString(R.string.exporterFailed429))
                                )
                                addAction(0, getString(R.string.tryAgain), pi(c, ACTION_START))
                            }
                            else -> eventNotification(Notify.ID_EXPORTER_UNK_FETCH_ERROR) {
                                setContentTitle(getString(R.string.exporterFailed))
                                setStyle(
                                    NotificationCompat.BigTextStyle().bigText(
                                        getString(R.string.exporterFailedUnk, code.toString())
                                    )
                                )
                                addAction(0, getString(R.string.tryAgain), pi(c, ACTION_START))
                            }
                        }
                        finish(false)
                    }
                }
            }
        }
        handle()
    }

    private fun handle() {
        CoroutineScope(Dispatchers.IO).launch {
            exp = dao.exportables().sortedBy { it.addedAt }.getOrNull(0)
            withContext(Dispatchers.Main) {
                if (exp == null) this@Exporter.finish(false)
                else {
                    exp?.opt = Exportable.Options.parse(exp?.options)
                    exp?.fetchData()
                }
            }
        }
    }

    private fun Exportable.fetchData() {
        threadData?.items?.sortBy { it.timestamp }
        FetchOfThread(
            this@Exporter, thread, threadData?.items?.firstOrNull()?.item_id ?: "",
            handler, reqQueue, 75
        ).start()
    }

    private suspend fun Exportable.fetchMedia() {
        if (threadData?.items == null) {
            end(this, false); return; }
        media = hashMapOf()
        if (opt?.img() == false && opt?.vid() == false && opt?.voi() == false) {
            fetchMedium(); return; }

        threadData?.thread_id?.also {
            cacheDir = File(Cache(c), it)
            if (!cacheDir!!.exists()) cacheDir!!.mkdir()
        }
        val img = opt?.img() == true
        val vid = opt?.vid() == true
        val actVid = opt?.actVid() == true
        if (img) for (user in threadData!!.users) {
            val key = USER_PROFILE_IMG.format(user.pk)
            media[key] = Downloadable(user.profile_pic_url, 0, cacheDir!!, key, 0)
        }
        for (dm in threadData!!.items) {
            if (actVid && dm.animated_media != null) continue
            // HTML gets GIFs dynamically, PDF cannot show them properly, and TXT...
            if (dm.voice_media != null) {
                if (opt?.voice == 0 && dm.voice_media.media != null)
                    media[dm.item_id] = Downloadable(
                        dm.voice_media.media.audio.audio_src, 2, cacheDir!!, dm.item_id, -2
                    )
                continue; }
            if (opt?.img() == true || opt?.vid() == true) (when {
                vid && dm.clip != null -> dm.clip.clip
                dm.direct_media_share != null -> when (dm.direct_media_share.media.media_type) {
                    1f, 2f -> if (img || vid) dm.direct_media_share.media else null
                    8f -> dm.direct_media_share.media.carousel_media
                        ?.let { if (img || vid) it[0] else null }

                    else -> null
                }
                vid && dm.felix_share != null -> dm.felix_share.video
                dm.media != null -> if (img || vid) dm.media else null
                dm.media_share != null -> when (dm.media_share.media_type) {
                    1f, 2f -> if (img || vid) dm.media_share else null
                    8f -> dm.media_share.carousel_media?.let { if (img || vid) it[0] else null }
                    else -> null
                }
                img && dm.raven_media != null -> dm.raven_media
                dm.reel_share != null -> if (img || vid) dm.reel_share.media else null
                dm.story_share != null -> if (img || vid) dm.story_share.media else null
                else -> null
            })?.apply {
                if (carousel_media == null && image_versions2 == null) return@apply
                val theVer = carousel_media?.getOrNull(0) ?: this
                val quality = when {
                    theVer.video_versions != null && opt!!.video == 3 ->
                        if (img) -opt!!.image.toFloat() else Versioned.MEDIUM
                    theVer.video_versions != null -> -opt!!.video.toFloat()
                    else -> -opt!!.image.toFloat()
                }
                val url = theVer.nearest(quality, justImage = opt?.actVid() != true) ?: return@apply
                media[dm.item_id] = Downloadable(
                    url, if (opt?.actVid() == true && video_versions != null) 1 else 0,
                    cacheDir!!, dm.item_id, quality.toInt()
                )
            }
        }
        fetchMedium()
    }

    private suspend fun Exportable.fetchMedium() {
        val queueSize: Int
        val dl: MutableMap.MutableEntry<String, Downloadable>?
        media.entries.filter { !it.value.cache.exists() }.also {
            queueSize = it.size
            dl = it.getOrNull(0)
        }
        if (dl == null) {
            withContext(Dispatchers.Main) {
                threadData?.title()?.also { ntfText = c.getString(R.string.exporterWriting, it) }
                updateNotification()
            }
            export(); return; }
        val iDone = media.entries.size - queueSize
        val iAll = media.entries.size
        threadData?.title()?.also { title ->
            ntfText = c.getString(R.string.exporterFetchMedia, iDone + 1, iAll, title)
        }
        updateNotification(iDone to iAll)
        reqQueue.add(
            object : Request<ByteArray>(Method.GET, dl.value.url, Response.ErrorListener {
                CoroutineScope(Dispatchers.IO).launch {
                    media[dl.key]!!.write(byteArrayOf()) { fetchMedium() }
                }
            }) {
                override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!)

                override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> =
                    Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response))

                override fun deliverResponse(response: ByteArray) {
                    CoroutineScope(Dispatchers.IO).launch {
                        media[dl.key]!!.write(response) { fetchMedium() }
                    }
                }
            }.apply {
                setShouldCache(false)
                tag = "EXP_${dl.key}"
                retryPolicy = DefaultRetryPolicy(
                    Api.DEFAULT_TIMEOUT, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
                )
            })
    }

    private fun Exportable.export() {
        if (threadData?.items == null) {
            end(this, false); return; }
        when (type) {
            0 -> object : HtmlExporter(this@Exporter, this@export) {
                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(this@export, succeeded, rescueFolder?.uri)
                }
            }.start()

            1 -> object : PdfExporter(this@Exporter, this@export) {
                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(this@export, succeeded)
                }
            }.start()

            2 -> object : TxtExporter(this@Exporter, this@export) {
                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(this@export, succeeded)
                }
            }.start()

            else -> end(this, false)
        }
    } // Single-file exports do NOT need rescuing, they send files even to the cloud and virtual folders!

    private val endedOnes = arrayListOf<Long>()
    private fun end(oldExp: Exportable?, succeeded: Boolean, alternativeFolder: Uri? = null) {
        if (oldExp == null || oldExp.addedAt in endedOnes) return
        endedOnes.add(oldExp.addedAt)
        // Don't update notification here; it'll create another after onDestroy
        CoroutineScope(Dispatchers.IO).launch {
            dao.deleteExportable(oldExp)
            incrementCounter(Settings.spExportCount)
            withContext(Dispatchers.Main) {
                handle()
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
        val url: String, val type: Short, folder: File, dmId: String, quality: Int
    ) { // TYPE: 0=>IMG, 1=>VID, 2=>AUD, 3=>GIF
        val cache = File(folder, "${dmId}_$quality.${fileTypes[type.toInt()].second}")

        fun fileName(dmId: String) = "${dmId}.${fileTypes[type.toInt()].second}"

        @Suppress("BlockingMethodInNonBlockingContext")
        suspend fun write(ba: ByteArray, then: suspend () -> Unit) {
            FileOutputStream(cache).use { fos -> fos.write(ba) }
            then()
        }
    }

    class Cache(c: Context) : File(c.cacheDir, "exporter")
}
