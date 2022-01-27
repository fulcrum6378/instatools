package ir.mahdiparastesh.instatools

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.IBinder
import androidx.documentfile.provider.DocumentFile
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.Downloads.Companion.UPDATE_FAILED
import ir.mahdiparastesh.instatools.Downloads.Companion.UPDATE_SUCCESS
import ir.mahdiparastesh.instatools.Downloads.Companion.handler
import ir.mahdiparastesh.instatools.data.Account
import ir.mahdiparastesh.instatools.data.PersonalDb
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.io.FileOutputStream

class Queuer : Service() {
    private lateinit var c: Context
    private lateinit var sp: SharedPreferences
    private lateinit var pDb: PersonalDb
    private lateinit var pDao: PersonalDb.DAO
    private lateinit var acc: Account

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent.action != null) when (intent.action) {
            ACTION_START -> {
                c = applicationContext
                sp = BaseActivity.esp(c)
                if (intent.extras != null)
                    intent.getParcelableExtra<Account>(EXTRA_USER)?.let { acc = it }
                if (::acc.isInitialized) {
                    pDb = PersonalDb.build(c, acc.id.toString()).also { pDao = it.dao() }
                    download()
                }
            }
            ACTION_STOP -> if (active) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun download() {
        val queue = pDao.readyQueueds().sortedBy { it.added }
        if (queue.isNullOrEmpty()) {
            stopSelf(); return; }
        if (queue[0].url == null) {
            handler?.obtainMessage(UPDATE_SUCCESS, queue[0])?.sendToTarget()
            pDao.deleteQueued(queue[0])
            download(); return; }

        Volley.newRequestQueue(c).add(
            object : Request<ByteArray>(Method.GET, queue[0].url, Response.ErrorListener {
                queue[0].failed = true
                handler?.obtainMessage(UPDATE_FAILED, queue[0])?.sendToTarget()
                pDao.updateQueued(queue[0])
                download()
            }) {
                override fun getHeaders(): Map<String, String> = Api.Headers(acc, sp)

                override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> =
                    Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response))

                override fun deliverResponse(response: ByteArray) {
                    save(queue[0], response)
                    handler?.obtainMessage(UPDATE_SUCCESS, queue[0])?.sendToTarget()
                    pDao.deleteQueued(queue[0])
                    download()
                }
            }
        )
    }

    private fun save(q: Queued, ba: ByteArray) {
        val stem = DocumentFile.fromTreeUri(c, Uri.parse(acc.folder!!))!!
        var branch = stem.findFile(q.userName)
        if (branch == null) branch = stem.createDirectory(q.userName)
        val type = MediaType.values().find { it.inDb == q.mediaType }!!
        val fName = "${q.itemId}.${type.ext}"
        var leaf = branch!!.findFile(fName)
        if (leaf == null) leaf = branch.createFile(type.mime, fName)
        c.contentResolver.openFileDescriptor(leaf!!.uri, "w")?.use { des ->
            FileOutputStream(des.fileDescriptor).use { fos -> fos.write(ba) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        active = true
    }

    override fun onDestroy() {
        super.onDestroy()
        active = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private val pack: String = Queuer::class.java.`package`!!.name
        val ACTION_START = "$pack.ACTION_START"
        val ACTION_STOP = "$pack.ACTION_STOP"
        val EXTRA_USER = "$pack.EXTRA_USER"
        var active = false
    }

    enum class MediaType(val mime: String, val ext: String, val inDb: Byte) {
        PHOTO("image/jpg", "jpg", 1),
        VIDEO("video/mp4", "mp4", 2)
    }
}
