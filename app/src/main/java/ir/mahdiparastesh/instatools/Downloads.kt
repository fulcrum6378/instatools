package ir.mahdiparastesh.instatools

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.widget.Toast
import ir.mahdiparastesh.instatools.data.GlobalDb
import ir.mahdiparastesh.instatools.data.PersonalDb
import ir.mahdiparastesh.instatools.data.Queued
import ir.mahdiparastesh.instatools.databinding.DownloadsBinding
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Media
import ir.mahdiparastesh.instatools.json.Profile.GraphQl
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.list.ListQud
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.util.*

class Downloads : BaseActivity() {
    lateinit var b: DownloadsBinding
    private lateinit var gDb: GlobalDb
    private lateinit var gDao: GlobalDb.DAO
    private lateinit var pDb: PersonalDb
    lateinit var pDao: PersonalDb.DAO

    companion object {
        const val UPDATE_SUCCESS = 0
        const val UPDATE_FAILED = 1
        const val ACTION_ADAPT = 2
        const val spStorage = "storage"
        var handler: Handler? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = DownloadsBinding.inflate(layoutInflater)
        setContentView(b.root)
        toolbar(b.toolbar, R.string.dwTitle)
        gDb = GlobalDb.build(c).also { gDao = it.dao() }
        m.acc = Login.gatherData(this, gDao)
        pDb = PersonalDb.build(c, (m.acc?.id ?: -1L).toString()).also { pDao = it.dao() }

        handler = object : Handler(Looper.getMainLooper()) {
            @SuppressLint("NotifyDataSetChanged")
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    UPDATE_SUCCESS -> if (m.queueds != null) Queued.find(
                        msg.obj as Queued, m.queueds!!
                    )?.let {
                        m.queueds!!.removeAt(it)
                        b.rv.adapter?.notifyItemRemoved(it)
                        if (it < m.queueds!!.size - 1)
                            b.rv.adapter?.notifyItemRangeChanged(it, m.queueds!!.size - 1)
                    }
                    UPDATE_FAILED -> m.queueds?.indexOf(msg.obj as Queued)?.let {
                        if (it == -1) return@let
                        m.queueds!![it] = msg.obj as Queued
                        b.rv.adapter?.notifyItemChanged(it)
                    }
                    ACTION_ADAPT ->
                        if (b.rv.adapter == null) b.rv.adapter = ListQud(this@Downloads)
                        else b.rv.adapter?.notifyDataSetChanged()
                }
            }
        }

        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { handleLink(it) }
        b.linkButton.setOnClickListener {
            handleLink(b.pasteLink.text.toString())
            b.pasteLink.setText("")
        }
    }

    override fun onResume() {
        super.onResume()
        Thread {
            m.queueds = ArrayList(pDao.queueds())
            m.queueds!!.sortBy { it.added }
            handler?.obtainMessage(ACTION_ADAPT)?.sendToTarget()
        }.start()
    }

    @Suppress("LABEL_NAME_CLASH")
    private fun handleLink(link: String) {
        if (link.contains("/stories/")) {
            val storyId = link.substringAfterLast("/").substringBefore("?")
            Api<GraphQl>(
                this, link.substringBefore("?") + "?__a=1", GraphQl::class, cache = true
            ) { graphQl ->
                val user = graphQl.user ?: return@Api
                Api<Rest.Reels>(
                    this, Api.Type.REELS.url.format(user.id), Rest.Reels::class, cache = true
                ) { reels ->
                    var med: Media? = reels.reels_media[0].items.find { it.pk == storyId }
                    if (med == null) med = reels.reels[user.id]?.items?.find { it.pk == storyId }
                    if (med == null) return@Api
                    val qud = Queued(
                        user.id, user.username, med.id, med.best(),
                        med.thumbnails?.sprite_urls?.getOrNull(0) ?: med.worst(),
                        med.media_type.toInt().toByte(), Calendar.getInstance().timeInMillis
                    )
                    qud.id = pDao.addQueued(qud)
                    m.queueds?.add(qud)
                    b.rv.adapter?.notifyItemInserted((m.queueds?.size ?: 1) - 1)
                    initService()
                }
            }
        } else Api<Media.MediaWrapperApi>(
            this, link.substringBefore("?") + "?__a=1", Media.MediaWrapperApi::class
        ) { wrapper ->
            val med = wrapper.items?.get(0) ?: return@Api
            var queued = true
            val items = arrayListOf<Queued>()
            when {
                med.carousel_media != null -> for (car in med.carousel_media) items.add(
                    Queued(
                        med.user.pk, med.user.username, car.id, car.best(),
                        med.thumbnails?.sprite_urls?.getOrNull(0) ?: car.worst(),
                        car.media_type.toInt().toByte(), Calendar.getInstance().timeInMillis
                    )
                )
                med.image_versions2 != null -> items.add(
                    Queued(
                        med.user.pk, med.user.username, med.id, med.best(),
                        med.thumbnails?.sprite_urls?.getOrNull(0) ?: med.worst(),
                        med.media_type.toInt().toByte(), Calendar.getInstance().timeInMillis
                    )
                )
                else -> {
                    queued = false
                    Toast.makeText(c, "Sorry dunno what to do!?!?", Toast.LENGTH_LONG).show()
                }
            }
            items.forEach {
                it.id = pDao.addQueued(it)
                m.queueds?.add(it)
                b.rv.adapter?.notifyItemInserted((m.queueds?.size ?: 1) - 1)
            }
            if (queued) initService()
        }
        // TODO: RECOGNISE BY ID LATER
    }

    fun initService() {
        if (Queuer.active) return
        var u = m.acc
        if (u != null && u.folder == null) u.folder = m.accounts.find { it.id == -1L }?.folder
        if (u == null)
            u = m.accounts.find { it.id == -1L }
        if (u!!.folder == null) {
            u.folder = sp.getString(spStorage, null) // TODO
        }
        startService(Intent(this, Queuer::class.java).apply {
            putExtra(Queuer.EXTRA_USER, u)
            action = Queuer.ACTION_START
        })
    }
}
