package ir.mahdiparastesh.instatools.serv

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.selection.Selection
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.PersonalDb
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.frag.PageSvd
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
import java.io.FileOutputStream
import java.util.*

class Queuer : ForegroundService() {
    private lateinit var pDb: PersonalDb
    private lateinit var pDao: PersonalDb.DAO
    private var dest: String? = null
    private var handlingLink: Queued? = null

    override val com: ForegroundServiceCompanion
        get() = Companion

    companion object : ForegroundServiceCompanion(262, Queuer::class) {
        override val channel: String = "$pack.DOWNLOADING"
        override var chName: Int = R.string.queuerChannel
        override var chDesc: Int = R.string.queuerChannelDesc
        override var ntfSmallIcon: Int = R.mipmap.launcher_round
        override var ntfTitle: Int = R.string.queuerTitle
        override var ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.queuerStop
        )
        override var active: Boolean = false
        override var handler: Handler? = null

        const val HANDLE_LINK = 0
        val ACTION_START = "$pack.ACTION_START"
        val EXTRA_LINK = "$pack.EXTRA_LINK"

        fun now() = Calendar.getInstance().timeInMillis
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent.action != null) when (intent.action) {
            ACTION_START -> if (intent.extras != null)
                intent.getStringExtra(EXTRA_LINK)?.let { handleLink(it) }
            ACTION_STOP -> if (active) destroy()
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        dest = preference(Settings.spStorage)
        if (m.acc == null || dest == null) {
            destroy(); return; }
        pDb = PersonalDb.build(c, m.acc!!.id.toString()).also { pDao = it.dao() }
        notification(Companion, Downloads::class)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LINK -> handleLink(msg.obj as String)
                    Api.HANDLE_ERROR -> if (handlingLink != null) {
                        handlingLink!!.failed = true
                        pDao.updateQueued(handlingLink!!)
                        Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, handlingLink!!)
                            ?.sendToTarget()
                        handlingLink = null
                        download()
                    }
                }
            }
        }
        download()
    }

    @Suppress("LABEL_NAME_CLASH")
    private fun handleLink(link: String, theQud: Queued? = null) {
        val qud = theQud ?: Queued(now(), link)
        if (theQud == null) {
            qud.id = pDao.addQueued(qud)
            Downloads.handler?.obtainMessage(Downloads.HANDLE_INSERTED, qud)?.sendToTarget()
        }
        handlingLink = qud

        if (link.contains("/stories/")) {
            val storyId = link.substringAfterLast("/").substringBefore("?")
            Api<Profile.GraphQl>(
                this, link.substringBefore("?") + "?__a=1", Profile.GraphQl::class,
                handler, cache = true
            ) { graphQl ->
                val user = graphQl.user
                if (user == null) {
                    handler?.obtainMessage(Api.HANDLE_ERROR)?.sendToTarget(); return@Api; }
                Api<Rest.Reels>(
                    this, Api.Type.REELS.url.format(user.id), Rest.Reels::class, handler,
                    cache = true
                ) { reels ->
                    var med: Media? = reels.reels_media[0].items.find { it.pk == storyId }
                    if (med == null) med = reels.reels[user.id]?.items?.find { it.pk == storyId }
                    if (med == null) {
                        handler?.obtainMessage(Api.HANDLE_ERROR)?.sendToTarget(); return@Api; }
                    qud.apply {
                        date = med.taken_at.toLong()
                        userId = user.id
                        userName = user.username
                        itemId = med.pk
                        url = med.best()
                        thumb = med.thumbnails?.sprite_urls?.getOrNull(0) ?: med.worst()
                        mediaType = med.media_type.toInt().toByte()
                    }
                    handlingLink = null
                    pDao.updateQueued(qud)
                    Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, qud)?.sendToTarget()
                    download()
                }
            }
        } else Api<Media.MediaWrapperApi>(
            this, link.substringBefore("?") + "?__a=1", Media.MediaWrapperApi::class,
            handler
        ) { wrapper ->
            val med = wrapper.items?.get(0)
            if (med == null) {
                handler?.obtainMessage(Api.HANDLE_ERROR)?.sendToTarget(); return@Api; }
            var found = true
            val addOns = arrayListOf<Queued>()
            when {
                med.carousel_media != null -> for (car in med.carousel_media)
                    if (qud.url == null) qud.apply {
                        userId = med.user.pk
                        userName = med.user.username
                        itemId = car.pk
                        url = car.best()
                        thumb = med.thumbnails?.sprite_urls?.getOrNull(0) ?: car.worst()
                        mediaType = car.media_type.toInt().toByte()
                    } else addOns.add(
                        Queued(
                            qud.addedAt, qud.link,
                            qud.date, med.user.pk, med.user.username, car.pk, car.best(),
                            med.thumbnails?.sprite_urls?.getOrNull(0) ?: car.worst(),
                            car.media_type.toInt().toByte()
                        )
                    )
                med.image_versions2 != null -> qud.apply {
                    date = med.taken_at.toLong()
                    userId = med.user.pk
                    userName = med.user.username
                    itemId = med.pk
                    url = med.best()
                    thumb = med.thumbnails?.sprite_urls?.getOrNull(0) ?: med.worst()
                    mediaType = med.media_type.toInt().toByte()
                }
                else -> {
                    found = false
                    Toast.makeText(c, "Unknown media!!?!", Toast.LENGTH_LONG).show()
                }
            }
            handlingLink = null
            if (found) {
                pDao.updateQueued(qud)
                Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, qud)?.sendToTarget()
                addOns.forEach { qud ->
                    qud.id = pDao.addQueued(qud)
                    Downloads.handler?.obtainMessage(Downloads.HANDLE_INSERTED, qud)?.sendToTarget()
                }
                download()
            }
        }
    }

    private var downloading = false
    private fun download() {
        if (downloading) return
        val queue = ArrayList(pDao.readyQueueds().sortedBy { it.date })
        if (queue.isNullOrEmpty()) {
            destroy(); return; }
        var q = 0
        while (queue[q].url == null) {
            if (handlingLink?.link != queue[q].link) handleLink(queue[q].link, queue[q])
            q++
            if (q >= queue.size) return
        }
        downloading = true

        Volley.newRequestQueue(c).add(
            object : Request<ByteArray>(Method.GET, queue[q].url, Response.ErrorListener {
                queue[q].failed = true
                Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, queue[q])?.sendToTarget()
                pDao.updateQueued(queue[q])
                downloading = false
                download()
            }) {
                override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!)

                override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> =
                    Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response))

                override fun deliverResponse(response: ByteArray) {
                    save(queue[q], response)
                    Downloads.handler?.obtainMessage(Downloads.HANDLE_DELETED, queue[q])
                        ?.sendToTarget()
                    pDao.deleteQueued(queue[q])
                    downloading = false
                    download()
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

    private fun save(q: Queued, ba: ByteArray) {
        val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
        var branch = stem.findFile(q.userName!!)
        if (branch == null) branch = stem.createDirectory(q.userName!!)
        val type = MediaType.values().find { it.inDb == q.mediaType }!!
        val fName = q.fName(type.ext)
        var leaf = branch!!.findFile(fName)
        if (leaf != null) return
        leaf = branch.createFile(type.mime, fName)
        c.contentResolver.openFileDescriptor(leaf!!.uri, "w")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos -> fos.write(ba) }
        } // TODO: RECOGNISE BY ID LATER
    }

    override fun onDestroy() {
        Downloads.handler?.obtainMessage(Downloads.HANDLE_RESET)?.sendToTarget()
        super.onDestroy()
    }

    class Saver(
        private val c: BaseActivity,
        private val pDao: PersonalDb.DAO,
        private val allPosts: List<Profile.Post>,
        selection: Selection<String>,
        private val unsave: Boolean,
        private val download: Boolean
    ) : Thread() {
        private val list = ArrayList(selection.toList())

        override fun run() {
            handle()
        }

        private fun handle() {
            val svd = list.getOrNull(0)
            if (svd == null) {
                if (download) Downloads.initService(c)
                return
            }
            allPosts.find { it.id == svd }?.let { post ->
                if (download) pDao.addQueued(
                    Queued(now(), Api.Type.POST.url.format(post.shortcode))
                )
                if (unsave) Api<Rest>(
                    c, Api.Type.UNSAVE.url.format(post.id), Rest::class, handler,
                    method = Request.Method.POST
                ) { rest ->
                    if (rest.status == "ok")
                        handler?.obtainMessage(PageSvd.HANDLE_UNSAVE_DONE, svd)?.sendToTarget()
                    ended()
                }
            }
            if (!unsave) ended()
        }

        private fun ended() {
            list.removeAt(0)
            handle()
        }
    }

    enum class MediaType(val mime: String, val ext: String, val inDb: Byte) {
        PHOTO("image/jpg", "jpg", 1),
        VIDEO("video/mp4", "mp4", 2)
    }
}
