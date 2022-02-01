package ir.mahdiparastesh.instatools.serv

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.Downloads
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Model
import ir.mahdiparastesh.instatools.data.PersonalDb
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.Persistent
import java.io.FileOutputStream
import java.util.*

@SuppressLint("UnspecifiedImmutableFlag")
class Queuer : Service(), ViewModelStoreOwner, Persistent {
    private var mViewModelStore = ViewModelStore()
    private lateinit var pDb: PersonalDb
    private lateinit var pDao: PersonalDb.DAO
    private var dest: String? = null
    private var handlingLink: Queued? = null

    override var c: Context
        get() = applicationContext
        set(_) {}
    override var m: Model
        get() = ViewModelProvider(viewModelStore, Model.Factory()).get("Model", Model::class.java)
        set(_) {}
    override var gsp: SharedPreferences
        get() = Persistent.initGsp(c)
        set(_) {}
    override var sp: SharedPreferences?
        get() = Persistent.initSp(c, m.acc)
        set(_) {}

    companion object {
        private val pack: String = Queuer::class.java.`package`!!.name
        val CHANNEL = "$pack.DOWNLOADING"
        val ACTION_START = "$pack.ACTION_START"
        val ACTION_STOP = "$pack.ACTION_STOP"
        val EXTRA_LINK = "$pack.EXTRA_LINK"
        const val CH_ID = 262
        const val HANDLE_LINK = 0
        var active = false
        var handler: Handler? = null

        fun now() = Calendar.getInstance().timeInMillis

        fun pi(c: Context, code: String): PendingIntent = PendingIntent.getService(
            c, 0, Intent(c, Queuer::class.java).apply { action = code },
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent.action != null) when (intent.action) {
            ACTION_START -> if (intent.extras != null)
                intent.getStringExtra(EXTRA_LINK)?.let { handleLink(it) }
            ACTION_STOP -> if (active) stopForeground(true)
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        active = true
        dest = preference(Settings.spStorage)
        if (m.acc == null || dest == null) stopSelf()
        pDb = PersonalDb.build(c, m.acc!!.id.toString()).also { pDao = it.dao() }
        download()

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL, c.resources.getString(R.string.queuerChannel),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = c.resources.getString(R.string.queuerChannelDesc) })
        startForeground(CH_ID, NotificationCompat.Builder(c, CHANNEL).apply {
            setSmallIcon(R.mipmap.launcher_round)
            setContentTitle(c.resources.getString(R.string.queuerTitle))
            setOngoing(true)
            priority = NotificationCompat.PRIORITY_LOW
            setContentIntent(
                PendingIntent.getActivity(
                    c, 0, Intent(c, Downloads::class.java), PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            addAction(0, c.resources.getString(R.string.queuerStop), pi(c, ACTION_STOP))
        }.build())
    }

    @Suppress("LABEL_NAME_CLASH")
    private fun handleLink(link: String, theQud: Queued? = null) {
        val qud = theQud ?: Queued(now(), "").apply { this.link = link }
        if (theQud == null) {
            qud.id = pDao.addQueued(qud)
            Downloads.handler?.obtainMessage(Downloads.HANDLE_INSERTED, qud)?.sendToTarget()
        }
        handlingLink = qud

        if (link.contains("/stories/")) {
            val storyId = link.substringAfterLast("/").substringBefore("?")
            Api<Profile.GraphQl>(
                this, link.substringBefore("?") + "?__a=1", Profile.GraphQl::class,
                cache = true, handleError = handler
            ) { graphQl ->
                val user = graphQl.user ?: return@Api
                Api<Rest.Reels>(
                    this, Api.Type.REELS.url.format(user.id), Rest.Reels::class,
                    cache = true, handleError = handler
                ) { reels ->
                    var med: Media? = reels.reels_media[0].items.find { it.pk == storyId }
                    if (med == null) med = reels.reels[user.id]?.items?.find { it.pk == storyId }
                    if (med == null) return@Api
                    qud.apply {
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
            handleError = handler
        ) { wrapper ->
            val med = wrapper.items?.get(0) ?: return@Api
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
                            qud.addedAt, qud.initiator,
                            qud.link, med.user.pk, med.user.username, car.pk, car.best(),
                            med.thumbnails?.sprite_urls?.getOrNull(0) ?: car.worst(),
                            car.media_type.toInt().toByte()
                        )
                    )
                med.image_versions2 != null -> qud.apply {
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
                    pDao.addQueued(qud)
                    Downloads.handler?.obtainMessage(Downloads.HANDLE_INSERTED, qud)?.sendToTarget()
                }
                download()
            }
        }
    }

    private var downloading = false
    private fun download() {
        if (downloading) return
        val queue = ArrayList(pDao.readyQueueds().sortedBy { it.addedAt })
        if (queue.isNullOrEmpty()) {
            stopSelf(); return; }
        var q = 0
        while (queue[q].url == null) {
            if (queue[q].link == null) {
                Downloads.handler?.obtainMessage(Downloads.HANDLE_DELETED, queue[0])?.sendToTarget()
                pDao.deleteQueued(queue[q])
                queue.removeAt(q)
            } else {
                if (handlingLink?.link != queue[q].link) handleLink(queue[q].link!!, queue[q])
                q++
            }
            if (q >= queue.size) return
        }
        downloading = true

        Volley.newRequestQueue(c).add(
            object : Request<ByteArray>(Method.GET, queue[0].url, Response.ErrorListener {
                queue[0].failed = true
                Downloads.handler?.obtainMessage(Downloads.HANDLE_CHANGED, queue[0])?.sendToTarget()
                pDao.updateQueued(queue[0])
                downloading = false
                download()
            }) {
                override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!, sp!!)

                override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> =
                    Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response))

                override fun deliverResponse(response: ByteArray) {
                    save(queue[0], response)
                    Downloads.handler?.obtainMessage(Downloads.HANDLE_DELETED, queue[0])
                        ?.sendToTarget()
                    pDao.deleteQueued(queue[0])
                    downloading = false
                    download()
                }
            }.apply {
                setShouldCache(false)
                tag = queue[0].itemId
                retryPolicy = DefaultRetryPolicy(
                    20000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
                )
            }
        )
    }

    private fun save(q: Queued, ba: ByteArray) {
        val stem = DocumentFile.fromTreeUri(c, Uri.parse(dest))!!
        var branch = stem.findFile(q.userName!!)
        if (branch == null) branch = stem.createDirectory(q.userName!!)
        for (f in branch!!.listFiles())
            if (f.name?.substringAfterLast("_")?.substringBefore(".") == q.itemId)
                return
        val type = MediaType.values().find { it.inDb == q.mediaType }!!// ?: return
        val fName = q.fName(type.ext)
        var leaf = branch.findFile(fName)
        if (leaf != null) return
        leaf = branch.createFile(type.mime, fName)
        c.contentResolver.openFileDescriptor(leaf!!.uri, "w")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos -> fos.write(ba) }
        } // TODO: RECOGNISE BY ID LATER
    }

    override fun onDestroy() {
        handler = null
        stopForeground(true)
        super.onDestroy()
        active = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    enum class MediaType(val mime: String, val ext: String, val inDb: Byte) {
        PHOTO("image/jpg", "jpg", 1),
        VIDEO("video/mp4", "mp4", 2)
    }

    override fun getViewModelStore(): ViewModelStore = mViewModelStore
}
