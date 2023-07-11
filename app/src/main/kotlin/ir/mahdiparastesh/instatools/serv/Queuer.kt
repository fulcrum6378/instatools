package ir.mahdiparastesh.instatools.serv

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ir.mahdiparastesh.instatools.*
import ir.mahdiparastesh.instatools.Settings.Companion.clearCacheIfNecessary
import ir.mahdiparastesh.instatools.Settings.Companion.incrementCounter
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.data.StorageCache
import ir.mahdiparastesh.instatools.json.*
import ir.mahdiparastesh.instatools.json.Api.Companion.adder
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.HumanDelay
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.ServiceOwnerActivity
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.UiTools
import ir.mahdiparastesh.instatools.view.UiTools.xFromSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class Queuer : ForegroundService() {
    private var dest: String? = null
    private var handlingLinks = CopyOnWriteArrayList<Link>()
    private var handlingLink = false
    private var download: BaseThread? = null
    private val stem by lazy { DocumentFile.fromTreeUri(c, Uri.parse(dest))!! }
    private val aliases = HashMap<String, String>()
    private val reqQueue by lazy { Volley.newRequestQueue(c) }
    private val tiffDate = SimpleDateFormat("yyyy:MM:dd kk:mm:ss", Locale.getDefault())

    override val com: ForegroundServiceCompanion get() = Companion
    override lateinit var ntfTitle: String
    override val requiresHandling = false

    companion object : ForegroundServiceCompanion() {
        override val klass = Queuer::class.java
        override val channel = Notify.Channel.QUEUER
        override val ntfId = Notify.ID_QUEUER
        override val ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.stop
        )

        const val HANDLE_LINK = 0
        const val HANDLE_HTML_ERROR = 1
        const val HANDLE_API_RES_ERROR = 2
        const val EXTRA_LINK = "link"
    }

    override fun resolveIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_LINK)?.let {
            handlingLinks.add(Link(it))
            handleLinks()
        }
    }

    override fun onCreate() {
        super.onCreate()
        dest = sPreference(Settings.spStorage)
        CoroutineScope(Dispatchers.IO).launch {
            Settings.loadAliases(this@Queuer, true)
                .forEach { (k, v) -> aliases[k] = v }
            if (sp != null) Settings.loadAliases(this@Queuer, false)
                .forEach { (k, v) -> aliases[k] = v }
        }
        if (m.acc == null || dest == null) {
            finish(false); return; }

        ntfManager.cancel(Notify.ID_QUEUER_SOME_FAILED)
        ntfTitle = getString(R.string.queuerTitle)
        initialNotification(Companion, Downloads::class)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LINK -> {
                        handlingLinks.add(Link(msg.obj as String))
                        handleLinks()
                    }
                    HANDLE_HTML_ERROR, Api.HANDLE_ERROR, HANDLE_API_RES_ERROR ->
                        handlingLinks.getOrNull(0)?.apply {
                            qud!!.status = 1.toByte()
                            CoroutineScope(Dispatchers.IO).launch { dao.updateQueued(qud!!) }
                            incrementCounter(Settings.spDlErrorCount)
                            Downloads.handler?.obtainMessage(
                                ServiceOwnerActivity.HANDLE_CHANGED, qud
                            )?.sendToTarget()
                            linkHandled()
                        }
                }
            }
        }
        if (download?.active != true) download = Download().also { it.start() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleLinks() {
        if (handlingLink) return
        val cur = handlingLinks.getOrNull(0)
        if (cur == null) {
            if (download?.active != true) finish(false)
            return; }
        handlingLink = true

        if (cur.qud == null) Thread {
            cur.qud = Queued(Persistent.now(), cur.link)
            cur.qud!!.id = dao.addQueued(cur.qud!!)
            Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_INSERTED, cur.qud!!)
                ?.sendToTarget()
        }.start()

        reqQueue.adder = object : StringRequest(cur.link, { html ->
            PageConfig.findFromHtml(html, false, {
                if (it is PageConfig.Companion.NeedAuth) needAuthentication()
                else {
                    Api.gotError(this@Queuer, handler, null, null, HANDLE_HTML_ERROR)
                    if (BuildConfig.DEBUG) throw it
                }
            }, null, null) { cnfWrapper ->
                if (cur.link.contains("/p/") || cur.link.contains("/reel/")
                    || cur.link.contains("/tv/")
                ) {
                    val shortcode = when {
                        cur.link.contains("/p/") -> cur.link.substringAfter("/p/")
                        cur.link.contains("/reel/") -> cur.link.substringAfter("/reel/")
                        cur.link.contains("/tv/") -> cur.link.substringAfter("/tv/")
                        else -> throw Exception("IMPOSSIBLE")
                    }.substringBefore("/")
                    reqQueue.adder = Api<GraphQl>(
                        this, Api.Endpoint.RAW_QUERY.url, GraphQl::class, handler,
                        Api.graphQlBody(cnfWrapper, shortcode),
                        method = Method.POST, autoQueue = false
                    ) { graphQl ->
                        val med =
                            graphQl.data?.xdt_api__v1__media__shortcode__web_info?.items?.firstOrNull()
                        if (med == null) {
                            handler?.obtainMessage(HANDLE_API_RES_ERROR)?.sendToTarget()
                            return@Api; }
                        var found = true
                        val addOns = arrayListOf<Queued>()
                        when {
                            med.carousel_media != null -> for (car in med.carousel_media!!)
                                if (cur.qud!!.url == null) cur.qud!!.apply {
                                    date = med.taken_at.xFromSeconds()
                                    userId = med.user.pk
                                    userName = med.user.username
                                    itemId = car.pk
                                    url = car.nearest(Versioned.BEST)
                                    thumb = med.thumb()
                                    mediaType = car.media_type.toInt().toByte()
                                    dur = car.video_duration?.toLong()
                                    caption = med.caption?.text
                                } else addOns.add(
                                    Queued(
                                        cur.qud!!.addedAt, cur.qud!!.link, cur.qud!!.date,
                                        med.user.pk, med.user.username,
                                        car.pk, car.nearest(Versioned.BEST),
                                        car.thumb(), car.media_type.toInt().toByte(),
                                        dur = car.video_duration?.toLong(),
                                        caption = med.caption?.text
                                    )
                                )
                            med.image_versions2 != null -> cur.qud!!.apply {
                                date = med.taken_at.xFromSeconds()
                                userId = med.user.pk
                                userName = med.user.username
                                itemId = med.pk
                                url = med.nearest(Versioned.BEST)
                                thumb = med.thumb()
                                mediaType = med.media_type.toInt().toByte()
                                dur = med.video_duration?.toLong()
                                caption = med.caption?.text
                            }
                            else -> found = false
                        }
                        if (found) handleQueued(cur.qud!!, addOns)
                        else {
                            linkHandled()
                            if (download?.active != true) finish(false)
                        }
                    }
                    return@findFromHtml; }
                // ELSE IF IT'S A STORY/HIGHLIGHT or an invalid link...

                val root = (cnfWrapper.require.keys
                    .find { it.startsWith("CometPlatformRootClient") }
                    ?.let { cnfWrapper.require[it] }?.getOrNull(2) as List<Any>?)
                    ?.getOrNull(3)?.let {
                        Gson().fromJson(Gson().toJson(it), PageConfig.PolarisRoot::class.java)
                    }
                if (root == null) {
                    Api.gotError(this@Queuer, handler, null, null, HANDLE_HTML_ERROR)
                    if (BuildConfig.DEBUG)
                        openFileOutput("${cur.qud?.addedAt}.json", 0)
                            .use { it.write(Gson().toJson(cnfWrapper).encodeToByteArray()) }
                    return@findFromHtml; }

                when (root.rootView.resource.__dr) {
                    "PolarisStoriesMediaRoot.react" -> reqQueue.adder =
                        Api<Rest.Reels<Rest.StoryReel>>(
                            this, Api.Endpoint.REEL_ITEM.url.format(root.rootView.props.user.id),
                            Rest.Reels::class, handler, autoQueue = false, cache = true,
                            typeToken = object : TypeToken<Rest.Reels<Rest.StoryReel>>() {}.type
                        ) { reels ->
                            val rel = reels.reels.getOrDefault(root.rootView.props.user.id, null)
                            val med = rel?.items?.find { it.pk == root.params.initial_media_id }
                            if (med == null) {
                                handler?.obtainMessage(HANDLE_API_RES_ERROR)
                                    ?.sendToTarget(); return@Api; }
                            cur.qud!!.apply {
                                date = med.taken_at.xFromSeconds()
                                userId = rel.user.pk
                                userName = rel.user.username
                                itemId = med.pk
                                url = med.nearest(Versioned.BEST)
                                thumb = med.thumb()
                                mediaType = med.media_type.toInt().toByte()
                                dur = med.video_duration?.toLong()
                                caption = med.caption?.text
                            }
                            handleQueued(cur.qud!!, null)
                        }
                    "PolarisStoriesHighlightsRoot.react" -> reqQueue.adder =
                        Api<Rest.Reels<Rest.HighlightReel>>(
                            this, Api.Endpoint.REEL_ITEM.url.format(
                                "highlight%3A${root.params.highlight_reel_id}"
                            ), Rest.Reels::class, handler, autoQueue = false, cache = true,
                            typeToken = object : TypeToken<Rest.Reels<Rest.HighlightReel>>() {}.type
                        ) { reels ->
                            val rel = reels.reels.getOrDefault(
                                "highlight:${root.params.highlight_reel_id}", null
                            )
                            val med = rel?.items?.find {
                                it.id == cur.link.substringAfter("story_media_id=")
                                    .substringBefore("&")
                            }
                            if (med == null) {
                                handler?.obtainMessage(HANDLE_API_RES_ERROR)
                                    ?.sendToTarget(); return@Api; }
                            cur.qud!!.apply {
                                date = med.taken_at.xFromSeconds()
                                userId = rel.user.pk
                                userName = rel.user.username
                                itemId = med.pk
                                url = med.nearest(Versioned.BEST)
                                thumb = med.thumb()
                                mediaType = med.media_type.toInt().toByte()
                                dur = med.video_duration?.toLong()
                                caption = med.caption?.text
                            }
                            handleQueued(cur.qud!!, null)
                        }
                    "PolarisProfileRoot.react" -> CoroutineScope(Dispatchers.IO)
                        .launch { dao.deleteQueued(cur.qud!!) }
                        .invokeOnCompletion { linkHandled() }
                    else -> {
                        Api.gotError(this@Queuer, handler, null, null, HANDLE_HTML_ERROR)
                        if (BuildConfig.DEBUG && root.rootView.resource.__dr != "PolarisErrorRoot.react") {
                            openFileOutput("unknown_api_${cur.qud?.addedAt}.json", 0)
                                .use { it.write(Gson().toJson(cnfWrapper).encodeToByteArray()) }
                            throw Exception(root.rootView.resource.__dr)
                        }
                    }
                }
            }
        }, {
            if (it.networkResponse?.statusCode == 429) {
                if (Downloads.active.value == true)
                    Downloads.handler?.obtainMessage(Downloads.HANDLE_429)?.sendToTarget()
                else eventNotification(Notify.ID_QUEUER_429) {
                    setContentTitle(getString(R.string.downloads))
                    setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            getString(R.string.queuer429)
                        )
                    )
                    setContentIntent(
                        PendingIntent.getActivity(
                            c, 0, Intent(c, Downloads::class.java), ntfMutability()
                        )
                    )
                }
                finish(true)
            } else {
                Api.gotError(this@Queuer, handler, null, it, HANDLE_HTML_ERROR)
                it.networkResponse?.data?.let { ba -> String(ba) }?.also { html ->
                    if (it.networkResponse?.statusCode == 500 &&
                        html.contains(Login.LOGGED_OUT_MSG_500)
                    ) needAuthentication()
                    else throw Exception(it.networkResponse?.statusCode?.toString())
                } // if it.networkResponse == null, just Api.gotError; slow internet connection!
            }
        }) {
            override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!, false)
        }.apply {
            setShouldCache(false)
            tag = "queuer_handle_link"
            retryPolicy = DefaultRetryPolicy(
                Api.DEFAULT_TIMEOUT, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        }
    }

    private fun handleQueued(qud: Queued, addOns: ArrayList<Queued>?) {
        CoroutineScope(Dispatchers.IO).launch {
            dao.updateQueued(qud)
            Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_CHANGED, qud)
                ?.sendToTarget()
            addOns?.forEach { qud ->
                qud.id = dao.addQueued(qud)
                Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_INSERTED, qud)
                    ?.sendToTarget()
            }
        }.invokeOnCompletion { linkHandled() }
    }

    private fun linkHandled() {
        handlingLinks.removeAt(0)
        handlingLink = false
        if (!active.value!!) return
        if (handlingLinks.isNotEmpty())
            HumanDelay(100L..3000L) { handleLinks() }
        if (download?.active != true) download = Download().also { it.start() }
    }

    inner class Download : BaseThread() {
        override fun run() {
            val queue = ArrayList(dao.readyQueueds())
            if (queue.isEmpty()) {
                if (!handlingLink) this@Queuer.finish(false)
                else interrupt()
                return; }

            super.run()
            var q = 0
            while (queue[q].url == null) {
                if (handlingLinks.all { it.link != queue[q].link }) {
                    handlingLinks.add(Link(queue[q].link, queue[q]))
                    handleLinks()
                }
                q++
                if (q >= queue.size) {
                    interrupt(); return; }
            }

            ntfTitle = getString(
                if (queue.size > 1) R.string.queuerTitleCount else R.string.queuerTitleCount1,
                queue.size
            )
            ntfSmallText = queue[q].userName
            updateNotification()
            if (queue[q].dur?.let { it > 600L } == true) {
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(
                    DownloadManager.Request(Uri.parse(queue[q].url)).apply {
                        setTitle(queue[q].userName)
                        setDescription(queue[q].caption)
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS, queue[q].fName(
                                MediaType.values().find { it.inDb == queue[q].mediaType }!!.ext
                            )
                        )
                    }
                )
                CoroutineScope(Dispatchers.IO)
                    .launch { dao.deleteQueued(queue[q]) }
                    .invokeOnCompletion { downloaded() }
            } else reqQueue.add(
                object : Request<ByteArray>(Method.GET, queue[q].url, Response.ErrorListener {
                    queue[q].status = 1.toByte()
                    Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_CHANGED, queue[q])
                        ?.sendToTarget()
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.updateQueued(queue[q])
                    }.invokeOnCompletion { downloaded() }
                    incrementCounter(Settings.spDlErrorCount)
                }) {
                    override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!)

                    override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> =
                        Response.success(
                            response.data, HttpHeaderParser.parseCacheHeaders(response)
                        )

                    override fun deliverResponse(response: ByteArray) {
                        Downloads.handler?.obtainMessage(
                            ServiceOwnerActivity.HANDLE_DELETED, queue[q]
                        )?.sendToTarget()
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching {
                                save(queue[q], response)
                                dao.deleteQueued(queue[q])
                            }.onSuccess {
                                downloaded()
                            }.onFailure {
                                if (BuildConfig.DEBUG) throw it else downloaded()
                            }
                        }
                    }
                }.apply {
                    setShouldCache(false)
                    tag = queue[q].itemId
                    retryPolicy = DefaultRetryPolicy(
                        Api.DEFAULT_TIMEOUT, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
                    )
                }
            )
        }
    }

    private fun downloaded() {
        download?.interrupt()
        if (!active.value!!) return
        download = Download().also { it.start() }
    }

    private fun save(q: Queued, ba: ByteArray) {
        val branch: DocumentFile = when {
            q.userName in aliases && DocumentFile.fromTreeUri(c, Uri.parse(aliases[q.userName]))
                ?.exists() == true ->
                DocumentFile.fromTreeUri(c, Uri.parse(aliases[q.userName]))
            !q.isMainFile() -> stem
            bPreference(Settings.spBranching, Settings.spBranchingCb, Settings.defSpBranching) ->
                stem.findFile(q.userName!!) ?: stem.createDirectory(q.userName!!)
            else -> stem
        } ?: return
        val type = MediaType.values().find { it.inDb == q.mediaType }!!
        val fName = q.fName(type.ext)
        var leaf = branch.findFile(fName)
        // Never check existence from StorageCache because the file might be deleted anytime and
        // the user might want to re-download it!
        if (leaf != null) return
        leaf = branch.createFile(type.mime, fName) ?: return
        // Nevertheless files are RARELY duplicated with a " (1)" suffix.
        // It presumably happens during slow connections.
        // It could be because of simultaneous writing.
        c.contentResolver.openFileDescriptor(leaf.uri, "w")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos ->
                when (q.mediaType) {
                    1.toByte() -> ExifRewriter().updateExifMetadataLossless(ba,
                        BufferedOutputStream(fos),
                        ((Imaging.getMetadata(ba) as JpegImageMetadata?)?.exif?.outputSet
                            ?: TiffOutputSet()).apply {
                            orCreateRootDirectory.apply {
                                removeField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION) // Title + Subject
                                add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, q.link)

                                removeField(ExifTagConstants.EXIF_TAG_SOFTWARE)
                                add(ExifTagConstants.EXIF_TAG_SOFTWARE, UiTools.APP_NAME)

                                removeField(TiffTagConstants.TIFF_TAG_ARTIST) // Authors
                                add(TiffTagConstants.TIFF_TAG_ARTIST, q.userName)

                                removeField(TiffTagConstants.TIFF_TAG_COPYRIGHT)
                                add(TiffTagConstants.TIFF_TAG_COPYRIGHT, "IG: @${q.userName}")
                            }
                            orCreateExifDirectory.apply {
                                removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                                add( // Date taken
                                    ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL,
                                    tiffDate.format(q.addedAt)
                                )

                                q.caption?.also {
                                    removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT)
                                    add(ExifTagConstants.EXIF_TAG_USER_COMMENT, it)
                                }

                                removeField(ExifTagConstants.EXIF_TAG_SITE)
                                add(ExifTagConstants.EXIF_TAG_SITE, q.link)
                            }
                        }) // location data is currently not possible with edge post location.
                    else -> fos.write(ba)
                }
            }
        }
        if (q.isMainFile()) m.files?.add(fName)
        incrementCounter(Settings.spDownloadCount)
    }

    override fun finish(cancelled: Boolean) {
        if (cancelled) reqQueue.cancelAll { true }
        destroy()
    }

    override fun destroy() {
        download?.interrupt()
        ntfTitle = getString(R.string.queuerTitle)
        ntfSmallText = null
        updateNotification()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                clearCacheIfNecessary()
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
            StorageCache.saveStorageCache(this@Queuer)
            val upToDate = dao.queueds()
            Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_RESET, 1, 0, upToDate)
                ?.sendToTarget()
            val failedSum = upToDate.filter { it.isFailed() }.size
            if (dao.readyQueueds().isNotEmpty()) { // double check in between
                download = Download().also { it.start() }; return@launch; }
            if (failedSum > 0) eventNotification(Notify.ID_QUEUER_SOME_FAILED) {
                setContentTitle(getString(R.string.queuerFailed, failedSum))
                setContentIntent(
                    PendingIntent.getActivity(
                        c, 0, Intent(c, Downloads::class.java), ntfMutability()
                    )
                )
            }
            super.destroy()
        }
    }

    enum class MediaType(val mime: String, val ext: String, val inDb: Byte) {
        PHOTO("image/jpg", "jpg", 1),
        VIDEO("video/mp4", "mp4", 2),
        AUDIO("audio/mp4", "m4a", 3),
    }

    data class Link(val link: String, var qud: Queued? = null)
}
