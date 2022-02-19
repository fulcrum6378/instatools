package ir.mahdiparastesh.instatools.serv

import android.os.Handler
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.more.ForegroundService

class Follower : ForegroundService() {
    override val com: ForegroundServiceCompanion
        get() = Companion

    companion object : ForegroundServiceCompanion(516, Follower::class) {
        override val channel: String = "$pack.FOLLOWING"
        override var chName: Int = R.string.followerChannel
        override var chDesc: Int = R.string.followerChannelDesc
        override var ntfSmallIcon: Int = R.mipmap.launcher_round
        override var ntfTitle: Int = R.string.followerTitle
        override var ntfActions: Array<Pair<String, Int>> = arrayOf(
            ACTION_STOP to R.string.followerStop,
            ACTION_PAUSE to R.string.followerPause,
        )
        override var active: Boolean = false
        override var handler: Handler? = null
    }

    override fun onCreate() {
        super.onCreate()
    }
}
