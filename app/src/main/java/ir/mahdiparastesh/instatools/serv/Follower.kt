package ir.mahdiparastesh.instatools.serv

import android.content.Intent
import android.os.*
import com.android.volley.Request
import ir.mahdiparastesh.instatools.MassFollower
import ir.mahdiparastesh.instatools.R
import ir.mahdiparastesh.instatools.data.Database
import ir.mahdiparastesh.instatools.data.Followable
import ir.mahdiparastesh.instatools.data.Friend
import ir.mahdiparastesh.instatools.json.Api
import ir.mahdiparastesh.instatools.json.Rest
import ir.mahdiparastesh.instatools.more.Delay
import ir.mahdiparastesh.instatools.more.ForegroundService
import ir.mahdiparastesh.instatools.more.LongThread
import kotlinx.coroutines.runBlocking

class Follower : ForegroundService() {
    private var toBeEnqueued = arrayListOf<ToBeEnqueued>()
    private var enqueuer: Enqueuer? = null
    private var scheduler: Scheduler? = null
    private var following = arrayListOf<Friend>()

    override val requiresHandling = true
    override val com: ForegroundServiceCompanion
        get() = Companion

    companion object : ForegroundServiceCompanion(77, Follower::class) {
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

        const val EXTRA_ENQUEUE = "enqueue"
        const val HANDLE_ENQUEUE = 0
        var DELAY = 10000L
    }

    override fun resolveIntent(intent: Intent) {
        intent.getParcelableExtra<ToBeEnqueued>(EXTRA_ENQUEUE)?.let { tbe ->
            toBeEnqueued.add(tbe)
            if (enqueuer?.active != true) enqueuer = Enqueuer().also { it.start() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        db = Database.build(c, m.acc!!.id.toString()).also { dao = it.dao() }
        notification(Follower, MassFollower::class)
        handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLE_ENQUEUE -> {
                        toBeEnqueued.add(msg.obj as ToBeEnqueued)
                        if (enqueuer?.active != true) enqueuer = Enqueuer().also { it.start() }
                    }
                }
            }
        }
        Thread {
            runBlocking { following.addAll(dao.following()) }
            if (scheduler?.active != true) scheduler = Scheduler().also { it.start() }
        }.start()
    }

    inner class Enqueuer : LongThread(handling.looper) {
        override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
            0 to { msg ->
                val flw = msg.obj as Rest.Follow
                var sum: Int
                dao.addFollowables(flw.users.filter {
                    (toBeEnqueued[0].includePv || !it.is_private) &&
                            it.pk !in following.map { f -> f.id }
                }.map { Followable(it.pk, it.username, it.is_private) }
                    .also { sum = it.size })
                if (flw.next_max_id == null) enqueuingDone() else allFollow(flw.next_max_id)
                if (sum > 0 && scheduler?.active != true)
                    scheduler = Scheduler().also { it.start() }
            },
            Api.HANDLE_ERROR to {
            }
        )

        override fun run() {
            super.run()
            enqueue()
        }

        private fun enqueue() {
            if (enqueuer?.active == false) return
            val cur = toBeEnqueued.getOrNull(0)
            if (cur == null) {
                interrupt()
                if (scheduler?.active != true) this@Follower.destroy()
                return; }
            allFollow()
        }

        private fun allFollow(next_max_id: String = "") {
            Api<Rest.Follow>(
                this@Follower,
                (if (toBeEnqueued[0].isItFollowers) Api.Type.FOLLOWERS else Api.Type.FOLLOWING).url
                    .format(toBeEnqueued[0].id, next_max_id), Rest.Follow::class, handler
            ) { flw -> handler?.obtainMessage(0, flw)?.sendToTarget() }
        }

        private fun enqueuingDone() {
            toBeEnqueued.removeAt(0)
            enqueue()
        }
    }

    inner class Scheduler : LongThread(handling.looper) {
        override val messages: Array<Pair<Int, (msg: Message) -> Unit>> = arrayOf(
        )

        override fun run() {
            super.run()
            follow()
        }

        private fun follow() {
            val fwb = dao.aFollowable().getOrNull(0)
            if (fwb == null) {
                this@Follower.destroy()
                interrupt()
                return; }
            Api<Rest>(
                this@Follower, Api.Type.FOLLOW.url.format(fwb.id), Rest::class, null,
                method = Request.Method.POST, onError = {
                }) {
                dao.deleteFollowable(fwb)
                Delay(DELAY) { follow() }
            }
        }
    }

    class ToBeEnqueued(val id: String, val isItFollowers: Boolean, val includePv: Boolean) :
        Parcelable {
        constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readByte() != 0.toByte(),
            parcel.readByte() != 0.toByte()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(id)
            parcel.writeByte(if (isItFollowers) 1 else 0)
            parcel.writeByte(if (includePv) 1 else 0)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<ToBeEnqueued> {
            override fun createFromParcel(parcel: Parcel): ToBeEnqueued = ToBeEnqueued(parcel)
            override fun newArray(size: Int): Array<ToBeEnqueued?> = arrayOfNulls(size)
        }
    }
}
