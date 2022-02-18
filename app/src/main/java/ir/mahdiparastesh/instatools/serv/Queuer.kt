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
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.BuildConfig
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Versioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.*

class Queuer : ForegroundService() {
    private var dest: String? = null
    private var handlingLink: Queued? = null
    private var download: BaseThread? = null

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
        dest = sPreference(Settings.spStorage)
        if (m.acc == null || dest == null) {
            destroy(); return; }
        db = Database.build(c, m.acc!!.id.toString()).also { dao = it.dao() }
        notification(Companion, Downloads::class)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_LINK -> handleLink(msg.obj as String)
                    Api.HANDLE_ERROR -> if (handlingLink != null) {
                        handlingLink!!.failed = true
                        Thread { dao.updateQueued(handlingLink!!) }.start()
                        Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, handlingLink!!)
                            ?.sendToTarget()
                        handlingLink = null
                        if (download?.active != true) download = Download().also { it.start() }
                    }
                }
            }
        }
        if (download?.active != true) download = Download().also { it.start() }
    }

    @Suppress("LABEL_NAME_CLASH")
    private fun handleLink(link: String, theQud: Queued? = null) {
        val qud = theQud ?: Queued(now(), link)
        if (theQud == null) Thread {
            qud.id = dao.addQueued(qud)
            Downloads.handler?.obtainMessage(Downloads.HANDLE_INSERTED, qud)?.sendToTarget()
        }.start()
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
                        url = med.nearest(Versioned.BEST)
                        thumb = med.thumbnails?.sprite_urls?.getOrNull(0)
                            ?: med.nearest(Versioned.WORST)
                        mediaType = med.media_type.toInt().toByte()
                    }
                    handlingLink = null
                    Thread {
                        dao.updateQueued(qud)
                        Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, qud)
                            ?.sendToTarget()
                    }.start()
                    if (download?.active != true) download = Download().also { it.start() }
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
                        date = med.taken_at.toLong()
                        userId = med.user.pk
                        userName = med.user.username
                        itemId = car.pk
                        url = car.nearest(Versioned.BEST)
                        thumb = med.thumbnails?.sprite_urls?.getOrNull(0)
                            ?: car.nearest(Versioned.WORST)
                        mediaType = car.media_type.toInt().toByte()
                    } else addOns.add(
                        Queued(
                            qud.addedAt, qud.link, qud.date, med.user.pk, med.user.username,
                            car.pk, car.nearest(Versioned.BEST),
                            med.thumbnails?.sprite_urls?.getOrNull(0)
                                ?: car.nearest(Versioned.WORST),
                            car.media_type.toInt().toByte()
                        )
                    )
                med.image_versions2 != null -> qud.apply {
                    date = med.taken_at.toLong()
                    userId = med.user.pk
                    userName = med.user.username
                    itemId = med.pk
                    url = med.nearest(Versioned.BEST)
                    thumb =
                        med.thumbnails?.sprite_urls?.getOrNull(0) ?: med.nearest(Versioned.WORST)
                    mediaType = med.media_type.toInt().toByte()
                }
                else -> {
                    found = false
                    Toast.makeText(c, "Unknown media!!?!", Toast.LENGTH_LONG).show()
                }
            }
            handlingLink = null
            if (found) CoroutineScope(Dispatchers.IO).launch {
                dao.updateQueued(qud)
                Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, qud)?.sendToTarget()
                addOns.forEach { qud ->
                    qud.id = dao.addQueued(qud)
                    Downloads.handler?.obtainMessage(Downloads.HANDLE_INSERTED, qud)
                        ?.sendToTarget()
                }
                withContext(Dispatchers.Main) {
                    if (download?.active != true) download = Download().also { it.start() }
                }
            }
        }
    }

    inner class Download : BaseThread() {
        override fun run() {
            super.run()
            val queue = ArrayList(dao.readyQueueds().sortedBy { it.date })
            if (queue.isNullOrEmpty()) {
                this@Queuer.destroy(); return; }
            var q = 0
            while (queue[q].url == null) {
                if (handlingLink?.link != queue[q].link) handleLink(queue[q].link, queue[q])
                q++
                if (q >= queue.size) return
            }

            Volley.newRequestQueue(c).add(
                object : Request<ByteArray>(Method.GET, queue[q].url, Response.ErrorListener {
                    queue[q].failed = true
                    Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, queue[q])
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
                        Downloads.handler?.obtainMessage(Downloads.HANDLE_DELETED, queue[q])
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
                    setShouldCache(false) // TODO: REALLY?!?
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
        download = Download().also { it.start() }
    }

    private fun save(q: Queued, ba: ByteArray) {
        val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
        // Sometimes the app's access to the Stem gets weak in a way that...
        // when you invoke "listFiles()" on it, it returns an empty list,
        // although it still can write into the Stem and create sub-folders in it.
        // In that case it starts creating "fulcrum1378 (1)", "fulcrum1378 (2)", etc...
        // It may be annoying but at least throws no exceptions :)
        var branch: DocumentFile?
        val shouldBranch = bPreference(Settings.spBranching, true) != false
        if (shouldBranch) {
            branch = stem.findFile(q.userName!!)
            if (branch == null) branch = stem.createDirectory(q.userName!!)
        } else branch = stem
        val type = MediaType.values().find { it.inDb == q.mediaType }!!
        val fName = q.fName(type.ext, !shouldBranch)
        var leaf = branch!!.findFile(fName)
        if (leaf != null) return
        leaf = branch.createFile(type.mime, fName)
        c.contentResolver.openFileDescriptor(leaf!!.uri, "w")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos -> fos.write(ba) }
        }
    }

    override fun destroy() {
        download?.interrupt()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (dest == null || bPreference(
                        Settings.spAutoDeleteEmptyDirs, Settings.defSpAutoDeleteEmptyDirs
                    ) != true
                ) return@runCatching
                val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
                for (branch in stem.listFiles())
                    if (branch.isDirectory && branch.listFiles().isEmpty())
                        branch.delete()
            }.onSuccess {
                super.destroy()
            }.onFailure {
                if (BuildConfig.DEBUG) throw it
                else super.destroy()
            }
        }
    }

    override fun onDestroy() {
        Downloads.handler?.obtainMessage(Downloads.HANDLE_RESET)?.sendToTarget()
        super.onDestroy()
    }

    enum class MediaType(val mime: String, val ext: String, val inDb: Byte) {
        PHOTO("image/jpg", "jpg", 1),
        VIDEO("video/mp4", "mp4", 2)
    }
}
