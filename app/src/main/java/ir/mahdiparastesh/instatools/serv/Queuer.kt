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
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.BaseThread
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.more.Versioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.FileOutputStream

class Queuer : ForegroundService() {
    private var dest: String? = null
    private var handlingLinks = arrayListOf<Link>()
    private var handlingLink = false
    private var download: BaseThread? = null

    override val requiresHandling = false
    override val com: ForegroundServiceCompanion get() = Companion

    companion object : ForegroundServiceCompanion(262, Queuer::class) {
        override val channel: String = "$pack.DOWNLOADING"
        override val chName: Int = R.string.queuerChannel
        override val chDesc: Int = R.string.queuerChannelDesc
        override val ntfSmallIcon: Int = R.mipmap.launcher_round
        override val ntfTitle: Int = R.string.queuerTitle
        override val ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.queuerStop
        )

        const val HANDLE_LINK = 0
        val EXTRA_LINK = "$pack.EXTRA_LINK"
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
        if (m.acc == null || dest == null) {
            destroy(); return; }
        notification(Companion, Downloads::class)
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
                        Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, qud)
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
            if (download?.active != true) destroy()
            return; }
        handlingLink = true

        if (cur.qud == null) Thread {
            cur.qud = Queued(Persistent.now(), cur.link)
            cur.qud!!.id = dao.addQueued(cur.qud!!)
            Downloads.handler?.obtainMessage(Downloads.HANDLE_INSERTED, cur.qud!!)?.sendToTarget()
        }.start()

        if (cur.link.contains("/stories/")) {
            val storyId = cur.link.substringAfterLast("/").substringBefore("?")
            Api<Profile.GraphQl>(
                this, cur.link.substringBefore("?") + "?__a=1", Profile.GraphQl::class,
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
                    cur.qud!!.apply {
                        date = med.taken_at.toLong()
                        userId = user.id
                        userName = user.username
                        itemId = med.pk
                        url = med.nearest(Versioned.BEST)
                        thumb = med.thumbnails?.sprite_urls?.getOrNull(0)
                            ?: med.nearest(Versioned.WORST)
                        mediaType = med.media_type.toInt().toByte()
                    }
                    handleQueued(cur.qud!!, null)
                }
            }
        } else Api<Media.MediaWrapperApi>(
            this, cur.link.substringBefore("?") + "?__a=1", Media.MediaWrapperApi::class,
            handler
        ) { wrapper ->
            val med = wrapper.items?.get(0)
            if (med == null) {
                handler?.obtainMessage(Api.HANDLE_ERROR)?.sendToTarget(); return@Api; }
            var found = true
            val addOns = arrayListOf<Queued>()
            when {
                med.carousel_media != null -> for (car in med.carousel_media)
                    if (cur.qud!!.url == null) cur.qud!!.apply {
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
                            cur.qud!!.addedAt, cur.qud!!.link, cur.qud!!.date,
                            med.user.pk, med.user.username,
                            car.pk, car.nearest(Versioned.BEST),
                            med.thumbnails?.sprite_urls?.getOrNull(0)
                                ?: car.nearest(Versioned.WORST),
                            car.media_type.toInt().toByte()
                        )
                    )
                med.image_versions2 != null -> cur.qud!!.apply {
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
            if (found) handleQueued(cur.qud!!, addOns)
            else {
                linkHandled()
                if (download?.active != true) destroy()
            }
        }
    }

    private fun handleQueued(qud: Queued, addOns: ArrayList<Queued>?) {
        CoroutineScope(Dispatchers.IO).launch {
            dao.updateQueued(qud)
            Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, qud)?.sendToTarget()
            addOns?.forEach { qud ->
                qud.id = dao.addQueued(qud)
                Downloads.handler?.obtainMessage(Downloads.HANDLE_INSERTED, qud)?.sendToTarget()
            }
        }.invokeOnCompletion { linkHandled() }
    }

    private fun linkHandled() {
        handlingLinks.removeAt(0)
        handlingLink = false
        if (handlingLinks.isNotEmpty()) handleLinks()
        if (download?.active != true) download = Download().also { it.start() }
    }

    inner class Download : BaseThread() {
        override fun run() {
            val queue = ArrayList(dao.readyQueueds().sortedBy { it.addedAt })
            if (queue.isNullOrEmpty()) {
                if (!handlingLink) this@Queuer.destroy()
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
        val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
        var branch: DocumentFile?
        val shouldBranch = bPreference(Settings.spBranching, Settings.defSpBranching)
        if (shouldBranch) {
            branch = stem.findFile(q.userName!!)
            if (branch == null) branch = stem.createDirectory(q.userName!!)
        } else branch = stem
        val type = MediaType.values().find { it.inDb == q.mediaType }!!
        val fName = q.fName(type.ext)
        var leaf = branch!!.findFile(fName)
        if (leaf != null) return
        leaf = branch.createFile(type.mime, fName)
        c.contentResolver.openFileDescriptor(leaf!!.uri, "w")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos -> fos.write(ba) }
        }
        sp?.edit()?.putLong(
            Settings.spDownloadCount, (sp?.getLong(Settings.spDownloadCount, 0L) ?: 0L) + 1L
        )?.commit()
    }

    override fun destroy() {
        download?.interrupt()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (dest == null ||
                    !bPreference(Settings.spAutoDeleteEmptyDirs, Settings.defSpAutoDeleteEmptyDirs)
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

    data class Link(val link: String, var qud: Queued? = null)
}
