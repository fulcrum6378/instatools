package ir.mahdiparastesh.instatools.serv

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.Settings.Companion.clearCacheIfNecessary
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.data.StorageCache
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.more.*
import ir.mahdiparastesh.instatools.view.Notify
import ir.mahdiparastesh.instatools.view.UiTools.Companion.xFromSeconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileOutputStream

class Queuer : ForegroundService() {
    private var dest: String? = null
    private var handlingLinks = arrayListOf<Link>()
    private var handlingLink = false
    private var download: BaseThread? = null
    private val stem by lazy { DocumentFile.fromTreeUri(c, Uri.parse(dest))!! }
    private val aliases = HashMap<String, String>()
    private val reqQueue by lazy { Volley.newRequestQueue(c) }

    override val requiresHandling = false
    override val com: ForegroundServiceCompanion get() = Companion

    companion object : ForegroundServiceCompanion() {
        override val klass = Queuer::class.java
        override val channel = Notify.Channel.QUEUER
        override val ntfId = Notify.ID_QUEUER
        override val ntfTitle = R.string.queuerTitle
        override val ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.stop
        )

        const val HANDLE_LINK = 0
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
            Settings.loadAliases(c, gsp).forEach { (k, v) -> aliases[k] = v }
            sp?.let { sp -> Settings.loadAliases(c, sp).forEach { (k, v) -> aliases[k] = v } }
        }
        if (m.acc == null || dest == null) {
            finish(false); return; }
        initialNotification(Companion, Downloads::class)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LINK -> {
                        handlingLinks.add(Link(msg.obj as String))
                        handleLinks()
                    }
                    Api.HANDLE_ERROR -> handlingLinks.getOrNull(0)?.apply {
                        qud!!.failed = true
                        Thread { dao.updateQueued(qud!!) }.start()
                        Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_CHANGED, qud)
                            ?.sendToTarget()
                        linkHandled()
                    }
                }
            }
        }
        if (download?.active != true) download = Download().also { it.start() }
    }

    @Suppress("LABEL_NAME_CLASH")
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

        when {
            cur.link.contains("/stor/") -> {
                Api<Media.MediaWrapperApi>(
                    this, Api.Endpoint.MEDIA_ITEM.url.format(
                        if (cur.link.contains("/stories/"))
                            cur.link.substringAfterLast("/").substringBefore("?")
                        else cur.link.substringAfter("story_media_id=")
                            .substringBefore("&")
                    ), Media.MediaWrapperApi::class, handler, cache = true
                ) { media ->
                    val med = media.items?.firstOrNull()
                    if (med == null) {
                        handler?.obtainMessage(Api.HANDLE_ERROR)?.sendToTarget(); return@Api; }
                    cur.qud!!.apply {
                        date = med.taken_at.xFromSeconds()
                        userId = med.user.pk
                        userName = med.user.username
                        itemId = med.pk
                        url = med.nearest(Versioned.BEST)
                        thumb = med.thumb()
                        mediaType = med.media_type.toInt().toByte()
                    }
                    handleQueued(cur.qud!!, null)
                }
            }
            // including "instagram.com/tv/" and "instagram.com/reel/"
            else -> {
                reqQueue.add(
                    object : StringRequest(cur.link, { html ->
                        Api<Media.MediaWrapperApi>(
                            this, Api.Endpoint.MEDIA_ITEM.url.format(
                                html.substringAfter("content=\"instagram://media?id=")
                                    .substringBefore("\"")
                            ), Media.MediaWrapperApi::class, handler
                        ) { wrapper ->
                            val med = wrapper.items?.getOrNull(0)
                            if (med == null) {
                                handler?.obtainMessage(Api.HANDLE_ERROR)
                                    ?.sendToTarget(); return@Api; }
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
                                    } else addOns.add(
                                        Queued(
                                            cur.qud!!.addedAt, cur.qud!!.link, cur.qud!!.date,
                                            med.user.pk, med.user.username,
                                            car.pk, car.nearest(Versioned.BEST),
                                            car.thumb(), car.media_type.toInt().toByte()
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
                                }
                                else -> {
                                    found = false
                                    Toast.makeText(c, "Unknown media!!?!", Toast.LENGTH_LONG).show()
                                }
                            }
                            if (found) handleQueued(cur.qud!!, addOns)
                            else {
                                linkHandled()
                                if (download?.active != true) finish(false)
                            }
                        }
                    }, { Api.gotError(handler, null, it) }) {
                        override fun getHeaders(): Map<String, String> =
                            Api.Headers(m.acc!!, false)
                    }
                )
            }
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
        if (handlingLinks.isNotEmpty()) handleLinks()
        if (download?.active != true) download = Download().also { it.start() }
    }

    inner class Download : BaseThread() {
        override fun run() {
            val queue = ArrayList(dao.readyQueueds().sortedBy { it.addedAt })
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

            reqQueue.add(
                object : Request<ByteArray>(Method.GET, queue[q].url, Response.ErrorListener {
                    queue[q].failed = true
                    Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_CHANGED, queue[q])
                        ?.sendToTarget()
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.updateQueued(queue[q])
                    }.invokeOnCompletion { downloaded() }
                }) {
                    override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!)

                    override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> =
                        Response.success(
                            response.data, HttpHeaderParser.parseCacheHeaders(response)
                        )

                    override fun deliverResponse(response: ByteArray) {
                        Downloads.handler?.obtainMessage(
                            ServiceOwnerActivity.HANDLE_DELETED,
                            queue[q]
                        )
                            ?.sendToTarget()
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
                        20000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
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
            bPreference(Settings.spBranching, Settings.defSpBranching) ->
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
        c.contentResolver.openFileDescriptor(leaf.uri, "w")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos -> fos.write(ba) }
        }
        m.files?.add(fName)
        gsp.edit().putLong(
            Settings.spDownloadCount, gsp.getLong(Settings.spDownloadCount, 0L) + 1L
        ).apply()
    }

    override fun finish(cancelled: Boolean) {
        if (!cancelled) Downloads.handler?.obtainMessage(Downloads.SHOW_AD)?.sendToTarget()
        destroy()
    }

    override fun destroy() {
        download?.interrupt()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                clearCacheIfNecessary()
                if (dest == null ||
                    !bPreference(Settings.spAutoDeleteEmptyDirs, Settings.defSpAutoDeleteEmptyDirs)
                ) return@runCatching
                val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
                for (branch in stem.listFiles())
                    if (branch.isDirectory && branch.listFiles().isEmpty())
                        branch.delete()
                StorageCache.saveStorageCache(this@Queuer)
            }.onSuccess {
                super.destroy()
            }.onFailure {
                if (BuildConfig.DEBUG) throw it
                else super.destroy()
            }
        }
    }

    override fun onDestroy() {
        Downloads.handler?.obtainMessage(ServiceOwnerActivity.HANDLE_RESET)?.sendToTarget()
        super.onDestroy()
    }

    enum class MediaType(val mime: String, val ext: String, val inDb: Byte) {
        PHOTO("image/jpg", "jpg", 1),
        VIDEO("video/mp4", "mp4", 2)
    }

    data class Link(val link: String, var qud: Queued? = null)
}
