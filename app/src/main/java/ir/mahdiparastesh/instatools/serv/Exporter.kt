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
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Exportable
import ir.mahdiparastesh.instatools.databinding.ListThdBinding
import ir.mahdiparastesh.instatools.frag.PageBox.FetchSomeDm
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Dm
import ir.mahdiparastesh.instatools.list.ListThd
import ir.mahdiparastesh.instatools.more.BaseActivity
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.view.PdfExporter

class Exporter : ForegroundService() {
    private lateinit var db: Database
    private lateinit var dao: Database.DAO
    private var exp: Exportable? = null

    override val com: ForegroundServiceCompanion
        get() = Companion

    companion object : ForegroundServiceCompanion(103, Exporter::class) {
        override val channel: String = "$pack.EXPORTING"
        override var chName: Int = R.string.exporterChannel
        override var chDesc: Int = R.string.exporterChannelDesc
        override var ntfSmallIcon: Int = R.mipmap.launcher_round
        override var ntfTitle: Int = R.string.exporterTitle
        override var ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.exporterStop
        )
        override var active: Boolean = false
        override var handler: Handler? = null
    }

    override fun onCreate() {
        super.onCreate()
        db = Database.build(c, m.acc!!.id.toString()).also { dao = it.dao() }
        notification(Companion, Main::class)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    FetchSomeDm.HANDLE_FETCHED_SOME -> {
                        val dmThd = msg.obj as Dm.DmThread
                        if (exp?.threadData == null) exp?.threadData = dmThd
                        else {
                            exp?.threadData?.items?.addAll(dmThd.items)
                            exp?.threadData?.has_older = dmThd.has_older
                        }
                        fetchSome()
                    }
                    Api.HANDLE_ERROR -> destroy()
                }
            }
        }
        handle()
    }

    private fun handle() {
        exp = dao.exportables().sortedBy { it.addedAt }.getOrNull(0)
        if (exp == null) {
            destroy(); return; }
        fetchSome()
    }

    private fun fetchSome() {
        if (exp == null) return
        exp!!.threadData?.items?.sortBy { it.timestamp }
        if (exp!!.threadData?.has_older == false) {
            export(); return; }
        FetchSomeDm(
            this, exp!!.thread, exp!!.threadData?.items?.getOrNull(0)?.item_id ?: "", handler
        ).start()
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

                override fun createView(c: Context, parent: ViewGroup, i: Int): View {
                    val b = ListThdBinding.inflate(
                        LayoutInflater.from(c).cloneInContext(
                            ContextThemeWrapper(c, BaseActivity.Theme.TERTIARY.res)
                        ), parent, false
                    )
                    ListThd.onCreate(
                        b, Typeface.createFromAsset(c.assets, c.getString(R.string.font_regular)),
                        Typeface.createFromAsset(c.assets, c.getString(R.string.font_light))
                    )
                    ListThd.onBind(c, b, list[i], list, i)
                    return b.root
                }
            }.start()
            (1).toByte() -> TODO()
            else -> end(exp)
        }
    }

    private fun end(oldExp: Exportable?) {
        if (oldExp == null) return
        dao.deleteExportable(oldExp)
        handle()
    }

    @Suppress("unused")
    enum class Method(val id: Byte, val mime: String, val ext: String) {
        PDF(0, "application/pdf", "pdf"),
        HTML(1, "text/html", "html")
    }
}
