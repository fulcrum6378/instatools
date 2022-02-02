package ir.mahdiparastesh.instatools.serv

import android.os.Handler
import android.os.Looper
import android.os.Message
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.PersonalDb
import ir.mahdiparastesh.instatools.data.Unfollower
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.ForegroundService

class Inquisitor : ForegroundService() {
    private lateinit var pDb: PersonalDb
    private lateinit var pDao: PersonalDb.DAO
    private var inquiry: Inquiry? = null

    override val com: ForegroundServiceCompanion
        get() = Companion

    companion object : ForegroundServiceCompanion(286, Inquisitor::class) {
        override val channel: String = "$pack.INQUIRING"
        override var chName: Int = R.string.inquiryChannel
        override var chDesc: Int = R.string.inquiryChannelDesc
        override var ntfSmallIcon: Int = R.mipmap.launcher_round
        override var ntfTitle: Int = R.string.inquiryTitle
        override var ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.inquiryStop
        )
        override var active: Boolean = false
        override var handler: Handler? = null
    }

    override fun onCreate() {
        super.onCreate()
        if (m.acc == null) destroy()
        pDb = PersonalDb.build(c, m.acc!!.id.toString()).also { pDao = it.dao() }
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    Api.HANDLE_ERROR -> {
                        PageUnf.theHandler?.obtainMessage(PageUnf.Action.ABORTED.ordinal)
                            ?.sendToTarget()
                        inquiry?.interrupt()
                        destroy()
                    }
                }
            }
        }

        inquiry = Inquiry().also { it.start() }
    }

    override fun onDestroy() {
        inquiry?.interrupt()
        super.onDestroy()
    }

    inner class Inquiry : BasePage.BaseThread() {
        private val following = arrayListOf<Rest.User>()

        override fun run() {
            super.run()
            allFollow()
        }

        private fun allFollow(next_max_id: String = "") {
            if (!active) return
            Api<Rest.Follow>(
                this@Inquisitor, Api.Type.FOLLOWING.url.format(m.acc!!.id, next_max_id),
                Rest.Follow::class, PageUnf.theHandler
            ) { flw ->
                following.addAll(flw.users.toMutableList())
                if (flw.next_max_id == null) analyse()
                else Delay { allFollow(flw.next_max_id) }
            }
        }

        private fun analyse(i: Int = 0) {
            if (!active) return
            if (i >= following.size) {
                PageUnf.theHandler?.obtainMessage(PageUnf.Action.COMPLETED.ordinal)?.sendToTarget()
                this@Inquisitor.destroy()
                return
            }
            Api<Profile>(
                this@Inquisitor, Api.Type.PROFILE.url.format(following[i].username),
                Profile::class, handler
            ) { profile ->
                val u = profile.graphql?.user
                if (u == null || u.follows_viewer != false || !active) return@Api
                val newbie = Unfollower(
                    u.id.toLong(), u.username, u.full_name, u.profile_pic_url,
                    u.edge_followed_by.count.toLong(), u.is_private == true
                )
                pDao.addUnfollower(newbie)
                PageUnf.theHandler?.obtainMessage(PageUnf.Action.ANALYSED.ordinal, newbie)
                    ?.sendToTarget()
            }
            Delay(500) { analyse(i + 1) }
        }
    }
}
