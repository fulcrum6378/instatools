package ir.mahdiparastesh.instatools.serv

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.Volley
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.frag.PageBox.FetchOfThread
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.view.PdfExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Exporter : ForegroundService() {
    private var exp: Exportable? = null
    private var media = hashMapOf<String, Downloadable>()

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
                        fetchData()
                    }
                    Api.HANDLE_ERROR -> destroy()
                }
            }
        }
        handle()
    }

    private fun handle() {
        CoroutineScope(Dispatchers.IO).launch {
            exp = dao.exportables().sortedBy { it.addedAt }.getOrNull(0)
            withContext(Dispatchers.Main) {
                if (exp == null)
                    this@Exporter.destroy()
                else fetchData()
            }
        }
    }

    private fun fetchData() {
        if (exp == null) return
        exp!!.threadData?.items?.sortBy { it.timestamp }
        if (exp!!.threadData?.has_older == false) {
            fetchMedia(); return; }
        FetchOfThread(
            this, exp!!.thread, exp!!.threadData?.items?.getOrNull(0)?.item_id ?: "", handler
        ).start()
    }

    private fun fetchMedia() {
        if (exp?.threadData?.items == null) {
            end(exp); return; }
        media = hashMapOf()
        for (dm in exp!!.threadData!!.items) (when {
            dm.clip != null -> dm.clip.clip
            dm.felix_share != null -> dm.felix_share.video
            dm.media != null -> dm.media
            dm.media_share != null -> dm.media_share
            dm.reel_share != null -> dm.reel_share.media
            dm.story_share != null -> dm.story_share.media
            else -> null
        })?.apply {
            if (carousel_media == null && image_versions2 == null) return@apply
            val url =
                if (carousel_media != null) carousel_media.getOrNull(0)?.nearest(justImage = true)
                else nearest(justImage = true) ?: return@apply
            media[dm.item_id] = Downloadable(url!!, null)
        }
        fetchMedium()
    }

    private fun fetchMedium() {
        val dl = media.entries.filter { it.value.data == null }.getOrNull(0)
        if (dl == null) {
            export(); return; }
        Volley.newRequestQueue(c).add(
            object : Request<ByteArray>(Method.GET, dl.value.url, Response.ErrorListener {
                media[dl.key] = media[dl.key]!!.apply { data = byteArrayOf() }
                Delay(MEDIA_DELAY) { fetchMedium() }
            }) {
                override fun getHeaders(): Map<String, String> = Api.Headers(m.acc!!)

                override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> =
                    Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response))

                override fun deliverResponse(response: ByteArray) {
                    media[dl.key] = media[dl.key]!!.apply { data = response }
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

    private fun export() {
        if (exp?.threadData?.items == null) {
            end(exp); return; }
        when (exp?.type) {
            (0).toByte() -> object : PdfExporter(c, Uri.parse(Api.encode(exp!!.uri))) {
                override val list: List<Dm> = exp!!.threadData!!.items

                override fun progress(percent: Float, succeeded: Boolean) {
                    if (percent == 100f) end(exp)
                }

                override fun createView(c: Context, parent: ViewGroup, i: Int): View =
                    ListThdBinding.inflate(
                        LayoutInflater.from(c).cloneInContext(
                            ContextThemeWrapper(c, BaseActivity.Theme.TERTIARY_LIGHT.res)
                        ), parent, false
                    ).onCreate(
                        Typeface.createFromAsset(c.assets, c.getString(R.string.font_regular)),
                        Typeface.createFromAsset(c.assets, c.getString(R.string.font_light))
                    ).onBind(c, list, i, downloaded = media).root
            }.start()
            // (1).toByte() ->
            else -> end(exp)
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
    enum class Method(val id: Byte, val mime: String, val ext: String) {
        PDF(0, "application/pdf", "pdf"),
        HTML(1, "text/html", "html")
    }

    class Downloadable(val url: String, var data: ByteArray?)
}
