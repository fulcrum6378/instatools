package ir.mahdiparastesh.instatools.serv

import android.os.Handler
import android.os.Looper
import android.os.Message
import ir.mahdiparastesh.instatools.Main
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Unfollower
import ir.mahdiparastesh.instatools.frag.PageUnf
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Profile
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.BasePage
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.ForegroundService

class Inquisitor : ForegroundService() {
    private var inquiry: Inquiry? = null
    var sumOfErrors = 0

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

        const val MAX_ERRORS = 10
        const val DELAY = 3000L
        const val DELAY_HURRY = 750L
        var hurry = false
    }

    override fun onCreate() {
        super.onCreate()
        if (m.acc == null) destroy()
        db = Database.build(c, m.acc!!.id.toString()).also { dao = it.dao() }
        notification(Companion, Main::class, 0)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    Api.HANDLE_ERROR -> { // Item Error
                        sumOfErrors++
                        if (sumOfErrors >= MAX_ERRORS) onAbort(false)
                    }
                }
            }
        }

        inquiry = Inquiry().also { it.start() }
    }

    override fun onAbort(cancelled: Boolean) {
        PageUnf.theHandler?.obtainMessage(BasePage.HANDLE_ABORTED)?.sendToTarget()
        inquiry?.interrupt()
        Thread { dao.deleteUnfollowers() }.start()
        super.onAbort(cancelled)
    }

    inner class Inquiry : BasePage.BaseThread() {
        private val following = arrayListOf<Rest.User>()
        private val unfollowers = arrayListOf<Unfollower>()

        override fun run() {
            super.run()
            dao.deleteUnfollowers()
            allFollow()
        }

        private fun allFollow(next_max_id: String = "") {
            if (!active) return
            Api<Rest.Follow>(
                this@Inquisitor, Api.Type.FOLLOWING.url.format(m.acc!!.id, next_max_id),
                Rest.Follow::class, PageUnf.theHandler
            ) { flw ->
                following.addAll(flw.users.toMutableList())
                PageUnf.theHandler?.obtainMessage(BasePage.HANDLE_FETCHED, following.size)
                    ?.sendToTarget()
                if (flw.next_max_id == null) analyse()
                else Delay { allFollow(flw.next_max_id) }
            }
        }

        private fun analyse(i: Int = 0) {
            if (!active) return
            if (i >= following.size) {
                PageUnf.theHandler?.obtainMessage(PageUnf.HANDLE_COMPLETED, unfollowers)
                    ?.sendToTarget()
                this@Inquisitor.destroy()
                return
            }
            PageUnf.theHandler?.obtainMessage(PageUnf.HANDLE_ANALYSED, i)?.sendToTarget()

            Api<Profile>(
                this@Inquisitor, Api.Type.PROFILE.url.format(following[i].username),
                Profile::class, handler
            ) { profile ->
                val u = profile.graphql?.user
                if (u == null || u.follows_viewer != false || !active) return@Api
                Unfollower(
                    u.id.toLong(), u.username, u.full_name, u.profile_pic_url,
                    u.edge_followed_by.count.toLong(), u.is_private == true
                ).apply {
                    dao.addUnfollower(this)
                    unfollowers.add(this)
                }
            }
            Delay(if (!hurry) DELAY else DELAY_HURRY) { analyse(i + 1) }
        }
    }
}
