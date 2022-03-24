package ir.mahdiparastesh.instatools.serv

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.Settings
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.frag.PageBox.FetchOfThread
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.Persistent
import ir.mahdiparastesh.instatools.view.HtmlExporter
import ir.mahdiparastesh.instatools.view.PdfExporter
import ir.mahdiparastesh.instatools.view.TxtExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Exporter : ForegroundService() {
    private var exp: Exportable? = null

    override val requiresHandling = false
    override val com: ForegroundServiceCompanion get() = Companion

    companion object : ForegroundServiceCompanion(103, Exporter::class) {
        override val channel: String = "$pack.EXPORTING"
        override val chName: Int = R.string.exporterChannel
        override val chDesc: Int = R.string.exporterChannelDesc
        override val ntfSmallIcon: Int = R.mipmap.launcher_round
        override val ntfTitle: Int = R.string.exporterTitle
        override val ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.exporterStop
        )

        const val MEDIA_DELAY = 200L

        fun canCreateDirSelf(c: Persistent) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                c.sPreference(Settings.spStorage) != null
    }

    override fun onCreate() {
        super.onCreate()
        notification(Companion, Main::class, 2)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    BasePage.HANDLE_FETCHED -> {
                        val dmThd = msg.obj as Dm.DmThread
                        if (exp?.threadData == null) exp?.threadData = dmThd
                        else {
                            exp?.threadData?.items?.addAll(dmThd.items)
                            exp?.threadData?.has_older = dmThd.has_older
                        }
                        exp?.fetchData()
                    }
                    Api.HANDLE_ERROR -> finish(false)
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
        if (threadData?.has_older == false) {
            fetchMedia(); return; }
        FetchOfThread(
            this@Exporter, thread, threadData?.items?.getOrNull(0)?.item_id ?: "", handler
        ).start()
    }

    private fun Exportable.fetchMedia() {
        if (threadData?.items == null) {
            end(this); return; }
        if (opt?.img() == false && opt?.vid() == false && opt?.voi() == false) {
            export(); return; }
        media = hashMapOf()
        val img = opt?.img() == true
        val vid = opt?.vid() == true
        for (dm in threadData!!.items) {
            if (vid && dm.animated_media != null) {
                media!![dm.item_id] = Downloadable(dm.animated_media.images.fixed_height.url, 3)
                continue; }
            if (opt?.voice == 0 && dm.voice_media != null) {
                media!![dm.item_id] = Downloadable(dm.voice_media.media.audio.audio_src, 2)
                continue; }
            if (opt?.img() == true || opt?.vid() == true) (when {
                vid && dm.clip != null -> dm.clip.clip
                vid && dm.felix_share != null -> dm.felix_share.video
                dm.media != null -> when (dm.media.media_type) {
                    1f -> if (img) dm.media else null
                    2f -> if (vid) dm.media else null
                    else -> null
                }
                dm.media_share != null -> when (dm.media_share.media_type) {
                    1f -> if (img) dm.media_share else null
                    2f -> if (vid) dm.media_share else null
                    else -> null
                }
                img && dm.raven_media != null -> dm.raven_media
                dm.reel_share != null -> when (dm.reel_share.media?.media_type) {
                    1f -> if (img) dm.reel_share.media else null
                    2f -> if (vid) dm.reel_share.media else null
                    else -> null
                }
                dm.story_share != null -> when (dm.story_share.media?.media_type) {
                    1f -> if (img) dm.story_share.media else null
                    2f -> if (vid) dm.story_share.media else null
                    else -> null
                }
                else -> null
            })?.apply {
                if (carousel_media == null && image_versions2 == null) return@apply
                val qua = -(if (vid) opt!!.video else opt!!.image).toFloat()
                val url = if (carousel_media != null) carousel_media!!.getOrNull(0)
                    ?.nearest(qua, justImage = !vid)
                else nearest(qua, justImage = !vid) ?: return@apply
                media!![dm.item_id] =
                    Downloadable(url!!, if (vid && video_versions != null) 1 else 0)
            }
        }
        fetchMedium()
    }

    private fun Exportable.fetchMedium() {
        val dl = media!!.entries.filter { it.value.data == null }.getOrNull(0)
        if (dl == null) {
            export(); return; }
        Volley.newRequestQueue(c).add(
            object : Request<ByteArray>(Method.GET, dl.value.url, Response.ErrorListener {
                media!![dl.key] = media!![dl.key]!!.apply { data = byteArrayOf() }
                Delay(MEDIA_DELAY) { fetchMedium() }
            }) {
                override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!)

                override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> =
                    Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response))

                override fun deliverResponse(response: ByteArray) {
                    media!![dl.key] = media!![dl.key]!!.apply { data = response }
                    Delay(MEDIA_DELAY) { fetchMedium() }
                }
            }.apply {
                setShouldCache(false)
                tag = "EXP_${dl.key}"
                retryPolicy = DefaultRetryPolicy(
                    15000, 2, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
                )
            })
    }

    private fun Exportable.export() {
        if (threadData?.items == null) {
            end(this); return; }
        when (type) {
            0 -> object : HtmlExporter(this@Exporter, this@export) {
                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(this@export)
                }
            }.start()
            1 -> object : PdfExporter(this@Exporter, this@export) {
                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(this@export)
                }
            }.start()
            2 -> object : TxtExporter(this@Exporter, this@export) {
                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(this@export)
                }
            }.start()
            else -> end(this)
        }
    }

    private fun end(oldExp: Exportable?) {
        if (oldExp == null) return
        CoroutineScope(Dispatchers.IO).launch {
            dao.deleteExportable(oldExp)
            withContext(Dispatchers.Main) { handle() }
        }
    }

    @Suppress("unused")
    enum class Method(
        val id: Int, val mime: String, val ext: String, val asTree: Boolean, val img: Boolean,
        val vid: Boolean,
    ) {
        HTML(0, "vnd.android.document/directory", "html", true, true, true),
        PDF(1, "application/pdf", "pdf", false, true, false),
        TXT(2, "text/plain", "txt", false, false, false),
    }

    class Downloadable(val url: String, val type: Short, var data: ByteArray? = null)
    // TYPE: 0=>IMG, 1=>VID, 2=>AUD, 3=>GIF
}
