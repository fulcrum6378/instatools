package ir.mahdiparastesh.instatools

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
import ir.mahdiparastesh.instatools.more.BaseActivity
import java.util.*

class Downloads : BaseActivity() {
    private lateinit var b: DownloadsBinding
    private lateinit var gDb: GlobalDb
    private lateinit var gDao: GlobalDb.DAO
    private lateinit var pDb: PersonalDb
    private lateinit var pDao: PersonalDb.DAO

    companion object {
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
            override fun handleMessage(msg: Message) {
            }
        }

        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { handleLink(it) }
        //https://www.instagram.com/tv/CZMS8OXBS_r/?utm_medium=share_sheet
        //https://instagram.com/stories/vesnaparapapa/2759673704843926757?utm_medium=share_sheet
    }

    private fun handleLink(link: String) {
        Api<Media.MediaWrapperApi>(
            this, link.substringBefore("?") + "?__a=1", Media.MediaWrapperApi::class
        ) { wrapper ->
            val med = wrapper.items[0]
            var queued = true
            val items = arrayListOf<Queued>()
            when {
                med.carousel_media != null -> for (car in med.carousel_media) items.add(
                    Queued(
                        -1L, med.user.pk, med.user.username, car.id, car.best(),
                        med.media_type.toInt().toByte(), Calendar.getInstance().timeInMillis
                    )
                )
                med.image_versions2 != null -> items.add(
                    Queued(
                        -1L, med.user.pk, med.user.username, med.id, med.best(),
                        med.media_type.toInt().toByte(), Calendar.getInstance().timeInMillis
                    )
                )
                else -> {
                    queued = false
                    Toast.makeText(c, "Sorry dunno what to do!?!?", Toast.LENGTH_LONG).show()
                }
            }
            items.forEach { pDao.addQueued(it) }
            if (queued) initService()
        }
        // TODO: RECOGNISE BY ID LATER
    }

    private fun initService() {
        var transmitted = m.acc
        transmitted?.let { acc ->
            if (acc.folder == null) acc.folder = m.accounts.find { it.id == -1L }?.folder
        }
        if (transmitted == null)
            transmitted = m.accounts.find { it.id == -1L }
        if (transmitted!!.folder == null) {
            transmitted.folder = sp.getString(spStorage, null) // TODO
        }
        startService(Intent(this, Queuer::class.java).apply {
            putExtra(Queuer.EXTRA_USER, transmitted)
            action = Queuer.ACTION_START
        })
    }
}
